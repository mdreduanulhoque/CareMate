package com.carebridge.security;

import com.carebridge.chat.ChatRoomAuthorizationService;
import com.carebridge.user.Role;
import com.carebridge.user.User;
import com.carebridge.user.UserRepository;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompAuthChannelInterceptor.class);

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String TOPIC_FEED_AVAILABILITY = "/topic/feed/availability";
    private static final String TOPIC_ADMIN_ALERTS = "/topic/admin/alerts";
    private static final String USER_QUEUE_NOTIFICATIONS = "/user/queue/notifications";
    private static final String USER_QUEUE_ERRORS = "/user/queue/errors";
    private static final String TOPIC_NEGOTIATION_PREFIX = "/topic/chat/negotiation/";
    private static final String TOPIC_DISPUTE_PREFIX = "/topic/chat/dispute/";
    private static final String APP_NEGOTIATION_PREFIX = "/app/chat/negotiation/";
    private static final String APP_DISPUTE_PREFIX = "/app/chat/dispute/";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ChatRoomAuthorizationService chatRoomAuthorizationService;

    public StompAuthChannelInterceptor(
            JwtService jwtService,
            UserRepository userRepository,
            ChatRoomAuthorizationService chatRoomAuthorizationService
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.chatRoomAuthorizationService = chatRoomAuthorizationService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            authenticateConnect(accessor);
        } else if (command == StompCommand.SUBSCRIBE) {
            authorizeSubscribe(accessor);
        } else if (command == StompCommand.SEND) {
            authorizeSend(accessor);
        }

        return message;
    }

    private void authenticateConnect(StompHeaderAccessor accessor) {
        String token = extractBearerToken(accessor);
        if (token == null) {
            throw new AccessDeniedException("Missing Bearer authentication token on WebSocket CONNECT.");
        }

        JwtService.JwtClaims claims;
        try {
            claims = jwtService.parseAndValidate(token);
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected STOMP CONNECT with invalid JWT: {}", ex.getMessage());
            throw new AccessDeniedException("Invalid or expired authentication token.");
        }

        User user = userRepository.findById(claims.userId())
                .filter(found -> found.isActive() && found.getRole() == claims.role())
                .orElseThrow(() -> new AccessDeniedException("User session is no longer valid."));

        CareBridgeUserPrincipal principal = new CareBridgeUserPrincipal(user);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );
        accessor.setUser(authentication);
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        CareBridgeUserPrincipal principal = requireAuthenticatedPrincipal(accessor.getUser());
        String destination = accessor.getDestination();
        if (destination == null || destination.isBlank()) {
            throw new AccessDeniedException("Subscription destination is required.");
        }

        if (TOPIC_FEED_AVAILABILITY.equals(destination)
                || USER_QUEUE_NOTIFICATIONS.equals(destination)
                || USER_QUEUE_ERRORS.equals(destination)) {
            chatRoomAuthorizationService.requireActivePrincipal(principal);
            return;
        }

        if (TOPIC_ADMIN_ALERTS.equals(destination)) {
            chatRoomAuthorizationService.requireActivePrincipal(principal);
            if (principal.getRole() != Role.ADMIN) {
                throw new AccessDeniedException("Admin privileges are required to subscribe to admin alerts.");
            }
            return;
        }

        if (destination.startsWith(TOPIC_NEGOTIATION_PREFIX)) {
            String roomId = extractSingleSegment(destination, TOPIC_NEGOTIATION_PREFIX);
            chatRoomAuthorizationService.authorizeNegotiationRoom(principal, roomId);
            return;
        }

        if (destination.startsWith(TOPIC_DISPUTE_PREFIX)) {
            String contractId = extractSingleSegment(destination, TOPIC_DISPUTE_PREFIX);
            chatRoomAuthorizationService.authorizeDisputeRoom(principal, contractId);
            return;
        }

        throw new AccessDeniedException("Subscription to destination '" + destination + "' is not permitted.");
    }

    private void authorizeSend(StompHeaderAccessor accessor) {
        CareBridgeUserPrincipal principal = requireAuthenticatedPrincipal(accessor.getUser());
        String destination = accessor.getDestination();
        if (destination == null || destination.isBlank()) {
            throw new AccessDeniedException("Message destination is required.");
        }

        if (destination.startsWith(APP_NEGOTIATION_PREFIX)) {
            String roomId = extractSingleSegment(destination, APP_NEGOTIATION_PREFIX);
            chatRoomAuthorizationService.authorizeNegotiationRoom(principal, roomId);
            return;
        }

        if (destination.startsWith(APP_DISPUTE_PREFIX)) {
            String contractId = extractSingleSegment(destination, APP_DISPUTE_PREFIX);
            chatRoomAuthorizationService.authorizeDisputeRoom(principal, contractId);
            return;
        }

        throw new AccessDeniedException("Sending messages to destination '" + destination + "' is not permitted.");
    }

    private String extractBearerToken(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null) {
            header = accessor.getFirstNativeHeader("authorization");
        }
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private CareBridgeUserPrincipal requireAuthenticatedPrincipal(Principal user) {
        if (user instanceof Authentication authentication
                && authentication.getPrincipal() instanceof CareBridgeUserPrincipal principal) {
            return principal;
        }
        throw new AccessDeniedException("WebSocket session is not authenticated.");
    }

    private String extractSingleSegment(String destination, String prefix) {
        String segment = destination.substring(prefix.length());
        if (segment.isBlank() || segment.contains("/") || segment.contains("*")) {
            throw new AccessDeniedException("Invalid destination segment.");
        }
        return segment;
    }
}
