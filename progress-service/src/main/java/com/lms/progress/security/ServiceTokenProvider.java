package com.lms.progress.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Mints short-lived service tokens for service-to-service calls made from event listeners, where no user JWT
 * is available. Signed with the shared JWT secret so every service's JwtAuthFilter accepts it.
 * Claims: sub=progress-service@lms.internal, userId=0, role=ADMIN, name=progress-service, 5 min expiry.
 */
@Component
public class ServiceTokenProvider {

    public static final String SERVICE_NAME = "progress-service";
    public static final String SUBJECT = SERVICE_NAME + "@lms.internal";
    static final Duration VALIDITY = Duration.ofMinutes(5);

    private final SecretKey key;

    public ServiceTokenProvider(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String createToken() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(SUBJECT)
                .claim("userId", 0L)
                .claim("role", Role.ADMIN.name())
                .claim("name", SERVICE_NAME)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(VALIDITY)))
                .signWith(key)
                .compact();
    }
}
