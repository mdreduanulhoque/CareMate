package com.carebridge.config;

import com.carebridge.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

/**
 * Produces clean, sanitized STOMP ERROR frames without leaking internal stack traces.
 */
@Component
public class CareBridgeStompErrorHandler extends StompSubProtocolErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(CareBridgeStompErrorHandler.class);

    @Override
    public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable ex) {
        Throwable root = unwrap(ex);
        String safeMessage;
        if (root instanceof AccessDeniedException accessDenied) {
            safeMessage = accessDenied.getMessage() != null
                    ? accessDenied.getMessage()
                    : "Access denied for WebSocket operation.";
        } else if (root instanceof BusinessException businessEx) {
            safeMessage = businessEx.getMessage();
        } else {
            log.warn("Unhandled STOMP client message error", ex);
            safeMessage = "WebSocket request could not be processed.";
        }

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setMessage(safeMessage);
        accessor.setLeaveMutable(true);

        if (clientMessage != null) {
            StompHeaderAccessor clientHeaders = StompHeaderAccessor.wrap(clientMessage);
            String receiptId = clientHeaders.getReceipt();
            if (receiptId != null) {
                accessor.setReceiptId(receiptId);
            }
        }

        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Throwable unwrap(Throwable ex) {
        Throwable current = ex;
        while (current instanceof MessageDeliveryException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
