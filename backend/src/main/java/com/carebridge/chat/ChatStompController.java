package com.carebridge.chat;

import com.carebridge.chat.dto.SendChatMessageRequest;
import com.carebridge.chat.dto.StompErrorResponse;
import com.carebridge.common.BusinessException;
import com.carebridge.security.CareBridgeUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class ChatStompController {

    private final ChatService chatService;

    public ChatStompController(ChatService chatService) {
        this.chatService = chatService;
    }

    @MessageMapping("/chat/negotiation/{roomId}")
    public void sendNegotiationMessage(
            @DestinationVariable String roomId,
            @Valid @Payload SendChatMessageRequest request,
            Principal principal
    ) {
        CareBridgeUserPrincipal userPrincipal = extractPrincipal(principal);
        chatService.sendNegotiationMessage(userPrincipal, roomId, request.content());
    }

    @MessageMapping("/chat/dispute/{contractId}")
    public void sendDisputeMessage(
            @DestinationVariable Long contractId,
            @Valid @Payload SendChatMessageRequest request,
            Principal principal
    ) {
        CareBridgeUserPrincipal userPrincipal = extractPrincipal(principal);
        chatService.sendDisputeMessage(userPrincipal, contractId, request.content());
    }

    @MessageExceptionHandler(BusinessException.class)
    @SendToUser("/queue/errors")
    public StompErrorResponse handleBusinessException(BusinessException ex) {
        return StompErrorResponse.of(ex.getStatus().name(), ex.getMessage());
    }

    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    @SendToUser("/queue/errors")
    public StompErrorResponse handleValidationException(MethodArgumentNotValidException ex) {
        String message = "Message content is invalid.";
        if (ex.getBindingResult() != null && ex.getBindingResult().getFieldError() != null) {
            String fieldMessage = ex.getBindingResult().getFieldError().getDefaultMessage();
            if (fieldMessage != null && !fieldMessage.isBlank()) {
                message = fieldMessage;
            }
        }
        return StompErrorResponse.of("BAD_REQUEST", message);
    }

    @MessageExceptionHandler(AccessDeniedException.class)
    @SendToUser("/queue/errors")
    public StompErrorResponse handleAccessDenied(AccessDeniedException ex) {
        return StompErrorResponse.of(
                "FORBIDDEN",
                ex.getMessage() != null ? ex.getMessage() : "You do not have permission to send messages here."
        );
    }

    private CareBridgeUserPrincipal extractPrincipal(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof CareBridgeUserPrincipal userPrincipal) {
            return userPrincipal;
        }
        throw new AccessDeniedException("Authenticated session is required to send chat messages.");
    }
}
