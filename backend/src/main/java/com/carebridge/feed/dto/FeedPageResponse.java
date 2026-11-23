package com.carebridge.feed.dto;

import java.util.List;

public record FeedPageResponse(
        List<FeedCaretakerCardResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
