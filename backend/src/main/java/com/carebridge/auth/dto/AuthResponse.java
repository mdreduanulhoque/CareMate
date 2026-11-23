package com.carebridge.auth.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UserSummaryResponse user
) {
    public static AuthResponse bearer(String accessToken, long expiresInSeconds, UserSummaryResponse user) {
        return new AuthResponse(accessToken, "Bearer", expiresInSeconds, user);
    }
}
