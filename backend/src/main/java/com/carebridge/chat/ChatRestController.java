package com.carebridge.chat;

import com.carebridge.chat.dto.ChatMessageResponse;
import com.carebridge.chat.dto.NegotiationRoomDetailResponse;
import com.carebridge.chat.dto.NegotiationRoomSummaryResponse;
import com.carebridge.chat.dto.SendChatMessageRequest;
import com.carebridge.security.CareBridgeUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chats")
@PreAuthorize("hasAnyRole('CUSTOMER', 'CARETAKER')")
public class ChatRestController {

    private final ChatService chatService;

    public ChatRestController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/rooms")
    public ResponseEntity<List<NegotiationRoomSummaryResponse>> listMyNegotiationRooms(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal
    ) {
        return ResponseEntity.ok(chatService.listNegotiationRooms(principal));
    }

    @GetMapping("/negotiation/{roomId}")
    public ResponseEntity<NegotiationRoomDetailResponse> getNegotiationRoomDetail(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal,
            @PathVariable String roomId,
            @RequestParam(name = "limit", defaultValue = "100") int limit
    ) {
        return ResponseEntity.ok(chatService.getNegotiationRoomDetail(principal, roomId, limit));
    }

    @GetMapping("/negotiation/{roomId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> getNegotiationMessages(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal,
            @PathVariable String roomId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "100") int size
    ) {
        return ResponseEntity.ok(chatService.getNegotiationMessages(principal, roomId, page, size));
    }

    @PostMapping("/negotiation/{roomId}/messages")
    public ResponseEntity<ChatMessageResponse> sendNegotiationMessage(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal,
            @PathVariable String roomId,
            @Valid @RequestBody SendChatMessageRequest request
    ) {
        ChatMessageResponse saved = chatService.sendNegotiationMessage(principal, roomId, request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
}
