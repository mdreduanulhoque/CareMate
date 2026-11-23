package com.carebridge.chat.dto;

import com.carebridge.caretaker.RateType;
import com.carebridge.user.Role;

import java.math.BigDecimal;
import java.time.Instant;

public record NegotiationRoomSummaryResponse(
        String roomId,
        Long customerId,
        Long caretakerUserId,
        Long counterpartUserId,
        String counterpartName,
        Role counterpartRole,
        String counterpartMobileNumber,
        String counterpartAvatarUrl,
        String counterpartQualification,
        String counterpartLocationArea,
        Boolean counterpartAvailable,
        BigDecimal counterpartRateAmount,
        RateType counterpartRateType,
        Instant unlockedAt,
        String lastMessageContent,
        Long lastMessageSenderId,
        Instant lastMessageAt
) {
}
