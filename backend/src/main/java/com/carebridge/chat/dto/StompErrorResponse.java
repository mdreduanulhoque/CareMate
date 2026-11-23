package com.carebridge.chat.dto;

import java.time.Instant;

public record StompErrorResponse(
        String code,
        String message,
        Instant timestamp
) {
    public static StompErrorResponse of(String code, String message) {
        return new StompErrorResponse(code, message, Instant.now());
    }
}
