package com.carebridge.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "carebridge.jwt")
public record JwtProperties(
        String secret,
        long expirationMs
) {
}
