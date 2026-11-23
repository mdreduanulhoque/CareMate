package com.carebridge.security;

import com.carebridge.user.Role;
import com.carebridge.user.User;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String STRONG_SECRET = "carebridge-unit-test-secret-key-at-least-32-bytes!";

    @Test
    void rejectsMissingOrShortSecretAtStartup() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("", 3600000)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtService(new JwtProperties("too-short-secret", 3600000)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void generatesAndValidatesTokenClaims() {
        JwtService jwtService = new JwtService(new JwtProperties(STRONG_SECRET, 3600000));
        User user = new User("Rahim Uddin", "01711112222", null, "hash", "Dhaka", Role.CUSTOMER);
        ReflectionTestUtils.setField(user, "id", 42L);

        String token = jwtService.generateToken(user);
        JwtService.JwtClaims claims = jwtService.parseAndValidate(token);

        assertThat(claims.userId()).isEqualTo(42L);
        assertThat(claims.mobileNumber()).isEqualTo("01711112222");
        assertThat(claims.role()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void rejectsTamperedToken() {
        JwtService jwtService = new JwtService(new JwtProperties(STRONG_SECRET, 3600000));
        User user = new User("Rahim Uddin", "01711112222", null, "hash", "Dhaka", Role.CUSTOMER);
        ReflectionTestUtils.setField(user, "id", 42L);

        String token = jwtService.generateToken(user);
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThatThrownBy(() -> jwtService.parseAndValidate(tampered))
                .isInstanceOf(JwtException.class);
    }
}
