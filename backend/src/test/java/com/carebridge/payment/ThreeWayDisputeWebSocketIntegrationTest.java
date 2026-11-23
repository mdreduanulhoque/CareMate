package com.carebridge.payment;

import com.carebridge.caretaker.drive.DriveLinkProbeClient;
import com.carebridge.caretaker.drive.DriveProbeResult;
import com.carebridge.chat.dto.ChatMessageResponse;
import com.carebridge.chat.dto.SendChatMessageRequest;
import com.carebridge.notification.dto.AdminQueueAlertEvent;
import com.carebridge.user.Role;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ThreeWayDisputeWebSocketIntegrationTest {

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
    void threeWayDisputeRoomDeliversRealtimeMessagesBlocksOutsidersAndLocksOnResolution() throws Exception {
        String adminToken = loginAsSeededAdmin();
        AuthInfo customer = registerCustomer("Dispute Relative", "01482000001", "Banani, Dhaka");
        AuthInfo outsider = registerCustomer("Outsider Snooper", "01482000002", "Mirpur, Dhaka");
        AuthInfo nurse = registerCaretaker("Dispute Nurse", "01482000003", "Banani, Dhaka");
        approveCaretaker(nurse.token(), adminToken);

        long contractId = createEndedContractAndMarkCustomerPaid(customer.token(), nurse.token(), nurse.userId());

        StompSession adminSession = null;
        StompSession customerSession = null;
        StompSession caretakerSession = null;
        StompSession outsiderSession = null;
        StompSession postResolveSendSession = null;

        try {
            // Admin subscribes to /topic/admin/alerts BEFORE caretaker triggers dispute
            BlockingQueue<AdminQueueAlertEvent> adminAlerts = new LinkedBlockingQueue<>();
            adminSession = connectWithJwt(adminToken, new StompSessionHandlerAdapter() {});
            adminSession.subscribe("/topic/admin/alerts", queueHandler(AdminQueueAlertEvent.class, adminAlerts));
            Thread.sleep(200);

            // Caretaker refuses payment -> triggers PAYMENT_DISPUTE Admin alert in real time
            ResponseEntity<String> refuseRes = postJson(
                    "/api/payments/contracts/" + contractId + "/caretaker-refuse",
                    nurse.token(),
                    """
                            { "caretakerStatement": "Did not receive bKash payment yet." }
                            """
            );
            assertThat(refuseRes.getStatusCode()).isEqualTo(HttpStatus.OK);

            AdminQueueAlertEvent disputeAlert = adminAlerts.poll(5, TimeUnit.SECONDS);
            assertThat(disputeAlert).isNotNull();
            assertThat(disputeAlert.eventType()).isEqualTo("PAYMENT_DISPUTE");
            assertThat(disputeAlert.entityId()).isEqualTo(contractId);

            // All 3 authorized parties join /topic/chat/dispute/{contractId}
            BlockingQueue<ChatMessageResponse> customerMsgs = new LinkedBlockingQueue<>();
            BlockingQueue<ChatMessageResponse> caretakerMsgs = new LinkedBlockingQueue<>();
            BlockingQueue<ChatMessageResponse> adminMsgs = new LinkedBlockingQueue<>();

            customerSession = connectWithJwt(customer.token(), new StompSessionHandlerAdapter() {});
            caretakerSession = connectWithJwt(nurse.token(), new StompSessionHandlerAdapter() {});

            String disputeTopic = "/topic/chat/dispute/" + contractId;
            customerSession.subscribe(disputeTopic, queueHandler(ChatMessageResponse.class, customerMsgs));
            caretakerSession.subscribe(disputeTopic, queueHandler(ChatMessageResponse.class, caretakerMsgs));
            adminSession.subscribe(disputeTopic, queueHandler(ChatMessageResponse.class, adminMsgs));
            Thread.sleep(250);

            // 1. Customer sends payment proof in 3-way room -> Caretaker & Admin receive it live
            customerSession.send(
                    "/app/chat/dispute/" + contractId,
                    new SendChatMessageRequest("Sharing bKash TrxID BK449911 sent at 4:00 PM.")
            );
            ChatMessageResponse caretakersCopy = caretakerMsgs.poll(5, TimeUnit.SECONDS);
            ChatMessageResponse adminsCopy = adminMsgs.poll(5, TimeUnit.SECONDS);
            customerMsgs.poll(5, TimeUnit.SECONDS);
            assertThat(caretakersCopy).isNotNull();
            assertThat(adminsCopy).isNotNull();
            assertThat(caretakersCopy.senderRole()).isEqualTo(Role.CUSTOMER);
            assertThat(adminsCopy.content()).contains("BK449911");

            // 2. Admin Mediator sends reply -> Customer & Caretaker both receive it live with ADMIN role
            adminSession.send(
                    "/app/chat/dispute/" + contractId,
                    new SendChatMessageRequest("Admin Mediator here: please verify last 4 digits of receiver number.")
            );
            ChatMessageResponse adminMsgForCustomer = customerMsgs.poll(5, TimeUnit.SECONDS);
            ChatMessageResponse adminMsgForCaretaker = caretakerMsgs.poll(5, TimeUnit.SECONDS);
            assertThat(adminMsgForCustomer).isNotNull();
            assertThat(adminMsgForCaretaker).isNotNull();
            assertThat(adminMsgForCustomer.senderRole()).isEqualTo(Role.ADMIN);

            // 3. Security: Outsider Customer subscribing to 3-way dispute room is rejected with STOMP ERROR
            CompletableFuture<String> outsiderError = new CompletableFuture<>();
            outsiderSession = connectWithJwt(outsider.token(), errorCapturingHandler(outsiderError));
            outsiderSession.subscribe(disputeTopic, queueHandler(ChatMessageResponse.class, new LinkedBlockingQueue<>()));
            assertThat(outsiderError.get(5, TimeUnit.SECONDS)).isNotBlank();

            // 4. Admin resolves dispute -> sending new STOMP messages to /app/chat/dispute/{id} is blocked
            ResponseEntity<String> resolveRes = postJson(
                    "/api/disputes/contract/" + contractId + "/resolve",
                    adminToken,
                    """
                            { "adminResolutionNote": "Verified settlement completed via mobile banking." }
                            """
            );
            assertThat(resolveRes.getStatusCode()).isEqualTo(HttpStatus.OK);

            CompletableFuture<String> postResolveError = new CompletableFuture<>();
            postResolveSendSession = connectWithJwt(customer.token(), errorCapturingHandler(postResolveError));
            postResolveSendSession.send(
                    "/app/chat/dispute/" + contractId,
                    new SendChatMessageRequest("Trying to send after resolution")
            );
            assertThat(postResolveError.get(5, TimeUnit.SECONDS)).containsIgnoringCase("resolved");
        } finally {
            disconnectQuietly(adminSession);
            disconnectQuietly(customerSession);
            disconnectQuietly(caretakerSession);
            disconnectQuietly(outsiderSession);
            disconnectQuietly(postResolveSendSession);
        }
    }

    private long createEndedContractAndMarkCustomerPaid(
            String customerToken,
            String caretakerToken,
            long caretakerUserId
    ) throws Exception {
        Instant start = Instant.now().minus(2, ChronoUnit.MINUTES);
        Instant end = Instant.now().plus(4, ChronoUnit.HOURS);

        ResponseEntity<String> createRes = postJson("/api/hires", customerToken, """
                {
                  "caretakerUserId": %d,
                  "agreedRate": 2200.00,
                  "rateType": "DAILY",
                  "scheduledStartTime": "%s",
                  "scheduledEndTime": "%s"
                }
                """.formatted(caretakerUserId, start.toString(), end.toString()));
        assertThat(createRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long contractId = objectMapper.readTree(createRes.getBody()).get("id").asLong();

        assertThat(postJson("/api/hires/" + contractId + "/accept", caretakerToken, "{}").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(postJson(
                "/api/hires/" + contractId + "/end-early",
                customerToken,
                "{\"reason\":\"Completed for 3-way dispute WebSocket test.\"}"
        ).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(postJson(
                "/api/payments/contracts/" + contractId + "/customer-done",
                customerToken,
                "{\"paymentReferenceNote\":\"bKash TrxID BK449911\"}"
        ).getStatusCode()).isEqualTo(HttpStatus.OK);

        return contractId;
    }

    private String wsUrl() {
        return "http://localhost:" + port + "/ws";
    }

    private StompSession connectWithJwt(String token, StompSessionHandlerAdapter handler) throws Exception {
        WebSocketHttpHeaders wsHeaders = new WebSocketHttpHeaders();
        wsHeaders.add("Origin", "http://localhost:5173");
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);
        return stompClient.connectAsync(wsUrl(), wsHeaders, connectHeaders, handler).get(5, TimeUnit.SECONDS);
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
                String msg = headers.getFirst("message");
                if (msg != null && !msg.isBlank()) {
                    errorFuture.complete(msg);
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
                errorFuture.complete(msg != null ? msg : "STOMP error");
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
                // Ignore disconnect race after intentional STOMP ERROR close
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

    private void approveCaretaker(String caretakerToken, String adminToken) throws Exception {
        ResponseEntity<String> appRes = postJson("/api/caretaker/applications", caretakerToken, """
                {
                  "dob": "1991-06-11",
                  "qualification": "BSc in Nursing",
                  "experience": "4 years clinical care",
                  "documentsDriveLink": "https://drive.google.com/file/d/public-dispute-ws/view"
                }
                """);
        assertThat(appRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long appId = objectMapper.readTree(appRes.getBody()).get("id").asLong();
        assertThat(postJson("/api/admin/applications/" + appId + "/approve", adminToken, "{}").getStatusCode())
                .isEqualTo(HttpStatus.OK);
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
