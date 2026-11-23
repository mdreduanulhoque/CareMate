package com.carebridge.security;

import com.carebridge.user.Role;
import com.carebridge.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(JwtProperties properties) {
        if (properties.secret() == null || properties.secret().isBlank()) {
            throw new IllegalStateException(
                    "carebridge.jwt.secret must be configured (via application-local.properties or CAREBRIDGE_JWT_SECRET)."
            );
        }
        byte[] secretBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "carebridge.jwt.secret must be at least 32 bytes (256 bits) for HS256 signing."
            );
        }
        if (properties.expirationMs() <= 0) {
            throw new IllegalStateException("carebridge.jwt.expiration-ms must be positive.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.expirationMs = properties.expirationMs();
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(expirationMs);

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("mobile", user.getMobileNumber())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public JwtClaims parseAndValidate(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Long userId = Long.parseLong(claims.getSubject());
        String mobile = claims.get("mobile", String.class);
        Role role = Role.valueOf(claims.get("role", String.class));
        return new JwtClaims(userId, mobile, role);
    }

    public long getExpirationSeconds() {
        return expirationMs / 1000L;
    }

    public record JwtClaims(Long userId, String mobileNumber, Role role) {
    }
}
