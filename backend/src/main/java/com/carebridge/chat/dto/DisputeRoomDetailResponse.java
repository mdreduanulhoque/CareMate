package com.carebridge.chat.dto;

import com.carebridge.payment.dto.PaymentDisputeResponse;

import java.util.List;

public record DisputeRoomDetailResponse(
        PaymentDisputeResponse dispute,
        List<ChatMessageResponse> messages,
        boolean hasMore
) {
}
