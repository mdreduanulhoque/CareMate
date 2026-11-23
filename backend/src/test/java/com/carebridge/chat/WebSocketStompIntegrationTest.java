package com.carebridge.chat;

import com.carebridge.caretaker.drive.DriveLinkProbeClient;
import com.carebridge.caretaker.drive.DriveProbeResult;
import com.carebridge.chat.dto.ChatMessageResponse;
import com.carebridge.chat.dto.SendChatMessageRequest;
import com.carebridge.notification.dto.AdminQueueAlertEvent;
import com.carebridge.notification.dto.FeedAvailabilityEvent;
import com.carebridge.notification.dto.UserNotificationEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketStompIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DriveLinkProbeClient driveLinkProbeClient;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setup() {
        when(driveLinkProbeClient.probe(anyString()))
                .thenReturn(new DriveProbeResult(true, "Publicly reachable (HTTP 200)."));

        SockJsClient sockJsClient = new SockJsClient(
                List.of(new WebSocketTransport(new StandardWebSocketClient()))
        );
        stompClient = new WebSocketStompClient(sockJsClient);
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(converter);
    }

    @Test
    void rejectsUnauthorizedStompConnectWithoutOrWithInvalidJwt() {
        WebSocketHttpHeaders wsHeaders = allowedOriginHeaders();

        StompHeaders missingAuth = new StompHeaders();
        assertThatThrownBy(() -> stompClient
                .connectAsync(wsUrl(), wsHeaders, missingAuth, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);

        StompHeaders invalidAuth = new StompHeaders();
        invalidAuth.add("Authorization", "Bearer invalid.jwt.token");
        assertThatThrownBy(() -> stompClient
                .connectAsync(wsUrl(), wsHeaders, invalidAuth, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
    }

    @Test
    void enforcesStompAuthorizationAndDeliversLiveNegotiationFeedAndAdminEvents() throws Exception {
        String adminToken = loginAsSeededAdmin();
        AuthInfo customerA = registerCustomer("Tanvir Ahmed", "01642000001", "Banani, Dhaka");
        AuthInfo customerIntruder = registerCustomer("Outsider User", "01642000002", "Mirpur, Dhaka");
        AuthInfo caretakerA = registerCaretaker("Nurse Hasina", "01642000003", "Banani, Dhaka");
        AuthInfo freshApplicant = registerCaretaker("New Applicant", "01642000004", "Uttara, Dhaka");

        // Approve caretakerA and unlock contact for customerA
        long appId = submitApplication(caretakerA.token(), "BSc in Nursing");
        approveApplication(appId, adminToken);
        String roomId = unlockContact(customerA.token(), caretakerA.userId());

        StompSession customerSession = null;
        StompSession caretakerSession = null;
        StompSession adminSession = null;
        StompSession intruderSubSession = null;
        StompSession intruderAdminTopicSession = null;
        StompSession intruderDirectTopicSendSession = null;
        StompSession intruderDisputeSubSession = null;

        try {
            // 1. Live 2-party STOMP negotiation chat + /user/queue/notifications
            BlockingQueue<ChatMessageResponse> customerMessages = new LinkedBlockingQueue<>();
            BlockingQueue<ChatMessageResponse> caretakerMessages = new LinkedBlockingQueue<>();
            BlockingQueue<UserNotificationEvent> caretakerNotifications = new LinkedBlockingQueue<>();
            BlockingQueue<FeedAvailabilityEvent> customerFeedEvents = new LinkedBlockingQueue<>();
            BlockingQueue<AdminQueueAlertEvent> adminAlerts = new LinkedBlockingQueue<>();

            customerSession = connectWithJwt(customerA.token(), new StompSessionHandlerAdapter() {});
            caretakerSession = connectWithJwt(caretakerA.token(), new StompSessionHandlerAdapter() {});
            adminSession = connectWithJwt(adminToken, new StompSessionHandlerAdapter() {});

            customerSession.subscribe("/topic/chat/negotiation/" + roomId, queueHandler(ChatMessageResponse.class, customerMessages));
            customerSession.subscribe("/topic/feed/availability", queueHandler(FeedAvailabilityEvent.class, customerFeedEvents));
            caretakerSession.subscribe("/topic/chat/negotiation/" + roomId, queueHandler(ChatMessageResponse.class, caretakerMessages));
            caretakerSession.subscribe("/user/queue/notifications", queueHandler(UserNotificationEvent.class, caretakerNotifications));
            adminSession.subscribe("/topic/admin/alerts", queueHandler(AdminQueueAlertEvent.class, adminAlerts));

            // Allow broker subscriptions to register before publishing
            Thread.sleep(250);

            customerSession.send(
                    "/app/chat/negotiation/" + roomId,
                    new SendChatMessageRequest("Can you cover night shift at Evercare Hospital?")
            );

            ChatMessageResponse deliveredToCaretaker = caretakerMessages.poll(5, TimeUnit.SECONDS);
            ChatMessageResponse deliveredToCustomer = customerMessages.poll(5, TimeUnit.SECONDS);
            assertThat(deliveredToCaretaker).isNotNull();
            assertThat(deliveredToCustomer).isNotNull();
            assertThat(deliveredToCaretaker.id()).isEqualTo(deliveredToCustomer.id());
            assertThat(deliveredToCaretaker.senderId()).isEqualTo(customerA.userId());
            assertThat(deliveredToCaretaker.content()).isEqualTo("Can you cover night shift at Evercare Hospital?");

            UserNotificationEvent pushedNotification = caretakerNotifications.poll(5, TimeUnit.SECONDS);
            assertThat(pushedNotification).isNotNull();
            assertThat(pushedNotification.eventType()).isEqualTo("NEW_CHAT_MESSAGE");
            assertThat(pushedNotification.roomId()).isEqualTo(roomId);

            // Caretaker replies over STOMP
            caretakerSession.send(
                    "/app/chat/negotiation/" + roomId,
                    new SendChatMessageRequest("Yes, confirmed for 8 PM.")
            );
            ChatMessageResponse replyForCustomer = customerMessages.poll(5, TimeUnit.SECONDS);
            assertThat(replyForCustomer).isNotNull();
            assertThat(replyForCustomer.senderId()).isEqualTo(caretakerA.userId());
            assertThat(replyForCustomer.content()).isEqualTo("Yes, confirmed for 8 PM.");

            // 2. Live Hunters Feed availability broadcast
            toggleAvailability(caretakerA.token(), false);
            FeedAvailabilityEvent availabilityEvent = customerFeedEvents.poll(5, TimeUnit.SECONDS);
            assertThat(availabilityEvent).isNotNull();
            assertThat(availabilityEvent.caretakerUserId()).isEqualTo(caretakerA.userId());
            assertThat(availabilityEvent.available()).isFalse();

            // 3. Live Admin queue alerts on new application
            submitApplication(freshApplicant.token(), "Diploma in Nursing");
            AdminQueueAlertEvent adminAlert = adminAlerts.poll(5, TimeUnit.SECONDS);
            assertThat(adminAlert).isNotNull();
            assertThat(adminAlert.eventType()).isEqualTo("NEW_APPLICATION");
            assertThat(adminAlert.caretakerUserId()).isEqualTo(freshApplicant.userId());

            // 4. Security: User C (intruder) subscribing to A & B's negotiation room receives STOMP ERROR
            CompletableFuture<String> intruderRoomError = new CompletableFuture<>();
            intruderSubSession = connectWithJwt(customerIntruder.token(), errorCapturingHandler(intruderRoomError));
            intruderSubSession.subscribe("/topic/chat/negotiation/" + roomId, queueHandler(ChatMessageResponse.class, new LinkedBlockingQueue<>()));
            String roomErrMsg = intruderRoomError.get(5, TimeUnit.SECONDS);
            assertThat(roomErrMsg).isNotBlank();

            // 5. Security: Non-admin subscribing to /topic/admin/alerts receives STOMP ERROR
            CompletableFuture<String> nonAdminTopicError = new CompletableFuture<>();
            intruderAdminTopicSession = connectWithJwt(customerIntruder.token(), errorCapturingHandler(nonAdminTopicError));
            intruderAdminTopicSession.subscribe("/topic/admin/alerts", queueHandler(AdminQueueAlertEvent.class, new LinkedBlockingQueue<>()));
            String adminTopicErrMsg = nonAdminTopicError.get(5, TimeUnit.SECONDS);
            assertThat(adminTopicErrMsg).containsIgnoringCase("Admin");

            // 6. Security: Direct SEND to /topic/** (bypassing /app) is blocked
            CompletableFuture<String> directTopicSendError = new CompletableFuture<>();
            intruderDirectTopicSendSession = connectWithJwt(customerIntruder.token(), errorCapturingHandler(directTopicSendError));
            intruderDirectTopicSendSession.send("/topic/feed/availability", new SendChatMessageRequest("spoof"));
            String directSendErrMsg = directTopicSendError.get(5, TimeUnit.SECONDS);
            assertThat(directSendErrMsg).isNotBlank();

            // 7. Security: Subscribing to inactive /topic/chat/dispute/{contractId} is rejected
            CompletableFuture<String> disputeSubError = new CompletableFuture<>();
            intruderDisputeSubSession = connectWithJwt(customerIntruder.token(), errorCapturingHandler(disputeSubError));
            intruderDisputeSubSession.subscribe("/topic/chat/dispute/999", queueHandler(ChatMessageResponse.class, new LinkedBlockingQueue<>()));
            String disputeErrMsg = disputeSubError.get(5, TimeUnit.SECONDS);
            assertThat(disputeErrMsg).isNotBlank();
        } finally {
            disconnectQuietly(customerSession);
            disconnectQuietly(caretakerSession);
            disconnectQuietly(adminSession);
            disconnectQuietly(intruderSubSession);
            disconnectQuietly(intruderAdminTopicSession);
            disconnectQuietly(intruderDirectTopicSendSession);
            disconnectQuietly(intruderDisputeSubSession);
        }
    }

    private String wsUrl() {
        return "http://localhost:" + port + "/ws";
    }

    private WebSocketHttpHeaders allowedOriginHeaders() {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Origin", "http://localhost:5173");
        return headers;
    }

    private StompSession connectWithJwt(String token, StompSessionHandlerAdapter handler) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);
        return stompClient
                .connectAsync(wsUrl(), allowedOriginHeaders(), connectHeaders, handler)
                .get(5, TimeUnit.SECONDS);
    }

    private <T> StompFrameHandler queueHandler(Class<T> payloadType, BlockingQueue<T> queue) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return payloadType;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.offer((T) payload);
            }
        };
    }

    private StompSessionHandlerAdapter errorCapturingHandler(CompletableFuture<String> errorFuture) {
        return new StompSessionHandlerAdapter() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                String messageHeader = headers.getFirst("message");
                if (messageHeader != null && !messageHeader.isBlank()) {
                    errorFuture.complete(messageHeader);
                }
            }

            @Override
            public void handleException(
                    StompSession session,
                    StompCommand command,
                    StompHeaders headers,
                    byte[] payload,
                    Throwable exception
            ) {
                String msg = headers != null && headers.getFirst("message") != null
                        ? headers.getFirst("message")
                        : exception.getMessage();
                errorFuture.complete(msg != null ? msg : "STOMP exception");
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                errorFuture.complete(exception.getMessage() != null ? exception.getMessage() : "Transport error");
            }
        };
    }

    private void disconnectQuietly(StompSession session) {
        if (session != null && session.isConnected()) {
            try {
                session.disconnect();
            } catch (RuntimeException ignored) {
                // Ignore teardown exceptions after intentional server-side error closure
            }
        }
    }

    private String loginAsSeededAdmin() throws Exception {
        ResponseEntity<String> res = postJson("/api/auth/login", null, """
                {
                  "mobileNumber": "01700000000",
                  "password": "AdminTestPass123!"
                }
                """);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(res.getBody()).get("accessToken").asText();
    }

    private AuthInfo registerCustomer(String name, String mobile, String address) throws Exception {
        ResponseEntity<String> res = postJson("/api/auth/register/customer", null, """
                {
                  "fullName": "%s",
                  "mobileNumber": "%s",
                  "address": "%s",
                  "password": "CustomerPassword123!"
                }
                """.formatted(name, mobile, address));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode root = objectMapper.readTree(res.getBody());
        return new AuthInfo(root.get("accessToken").asText(), root.get("user").get("id").asLong());
    }

    private AuthInfo registerCaretaker(String name, String mobile, String address) throws Exception {
        ResponseEntity<String> res = postJson("/api/auth/register/caretaker", null, """
                {
                  "fullName": "%s",
                  "mobileNumber": "%s",
                  "address": "%s",
                  "password": "CaretakerPassword123!"
                }
                """.formatted(name, mobile, address));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode root = objectMapper.readTree(res.getBody());
        return new AuthInfo(root.get("accessToken").asText(), root.get("user").get("id").asLong());
    }

    private long submitApplication(String caretakerToken, String qualification) throws Exception {
        ResponseEntity<String> res = postJson("/api/caretaker/applications", caretakerToken, """
                {
                  "dob": "1991-04-10",
                  "qualification": "%s",
                  "experience": "Hospital ICU ward attendant",
                  "documentsDriveLink": "https://drive.google.com/file/d/public-ws-test/view"
                }
                """.formatted(qualification));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readTree(res.getBody()).get("id").asLong();
    }

    private void approveApplication(long appId, String adminToken) {
        ResponseEntity<String> res = postJson("/api/admin/applications/" + appId + "/approve", adminToken, "{}");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private String unlockContact(String customerToken, long caretakerUserId) throws Exception {
        ResponseEntity<String> res = postJson("/api/contacts/unlock/" + caretakerUserId, customerToken, "{}");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(res.getBody()).get("roomId").asText();
    }

    private void toggleAvailability(String caretakerToken, boolean available) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(caretakerToken);
        ResponseEntity<String> res = restTemplate.exchange(
                "/api/caretaker/profile/availability",
                HttpMethod.PATCH,
                new HttpEntity<>("{\"available\":" + available + "}", headers),
                String.class
        );
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<String> postJson(String path, String bearerToken, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(json, headers), String.class);
    }

    private record AuthInfo(String token, long userId) {
    }
}
