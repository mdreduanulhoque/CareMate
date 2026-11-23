package com.carebridge.chat.dto;

import java.util.List;

public record NegotiationRoomDetailResponse(
        NegotiationRoomSummaryResponse room,
        List<ChatMessageResponse> messages,
        boolean hasMore
) {
}
