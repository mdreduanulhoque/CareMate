package com.carebridge.chat.dto;

import com.carebridge.chat.ChatMessage;
import com.carebridge.chat.ChatRoomType;
import com.carebridge.user.Role;
import com.carebridge.user.User;

import java.time.Instant;

public record ChatMessageResponse(
        Long id,
        String roomId,
        ChatRoomType roomType,
        Long senderId,
        String senderName,
        Role senderRole,
        Long contractId,
        String content,
        Instant sentAt
) {
    public static ChatMessageResponse from(ChatMessage message) {
        User sender = message.getSender();
        return new ChatMessageResponse(
                message.getId(),
                message.getRoomId(),
                message.getRoomType(),
                sender.getId(),
                sender.getFullName(),
                sender.getRole(),
                message.getContractId(),
                message.getContent(),
                message.getSentAt()
        );
    }
}
