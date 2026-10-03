package com.lms.user.security;

import com.lms.user.entity.Role;
import com.lms.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234567890";

    @Test
    void generatedToken_canBeParsed() {
        JwtService service = new JwtService(SECRET, 60_000);
        User user = User.builder().id(9L).name("Test").email("t@example.com").role(Role.INSTRUCTOR).build();

        Claims claims = service.parse(service.generateToken(user));

        assertThat(claims.getSubject()).isEqualTo("t@example.com");
        assertThat(((Number) claims.get("userId")).longValue()).isEqualTo(9L);
        assertThat(claims.get("role", String.class)).isEqualTo("INSTRUCTOR");
    }

    @Test
    void tokenSignedWithOtherSecret_isRejected() {
        JwtService a = new JwtService(SECRET, 60_000);
        JwtService b = new JwtService("another-secret-another-secret-0987654321", 60_000);
        User user = User.builder().id(1L).name("X").email("x@example.com").role(Role.STUDENT).build();

        assertThrows(JwtException.class, () -> b.parse(a.generateToken(user)));
    }
}
