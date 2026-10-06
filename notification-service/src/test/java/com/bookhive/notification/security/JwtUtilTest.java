package com.bookhive.notification.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** Tokens are created by user-service; this service must accept exactly those and nothing else. */
class JwtUtilTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";

    private static String token(String secret, UUID id, String role, long lifetimeMs) {
        return Jwts.builder()
                .subject(id.toString())
                .claim("name", "Ann")
                .claim("email", "ann@example.com")
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + lifetimeMs))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private final JwtUtil jwtUtil = new JwtUtil(SECRET);

    @Test
    void acceptsATokenSignedWithTheSharedSecret() {
        UUID id = UUID.randomUUID();

        AuthenticatedUser user = jwtUtil.parse(token(SECRET, id, "ADMIN", 60_000)).orElseThrow();

        assertThat(user.id()).isEqualTo(id);
        assertThat(user.name()).isEqualTo("Ann");
        assertThat(user.isAdmin()).isTrue();
    }

    @Test
    void rejectsATokenSignedWithAnotherSecret() {
        String forged = token("another-secret-another-secret-another-12", UUID.randomUUID(), "ADMIN", 60_000);

        assertThat(jwtUtil.parse(forged)).isEmpty();
    }

    @Test
    void rejectsAnExpiredToken() {
        assertThat(jwtUtil.parse(token(SECRET, UUID.randomUUID(), "MEMBER", -5_000))).isEmpty();
    }

    @Test
    void rejectsGarbage() {
        assertThat(jwtUtil.parse("nope")).isEmpty();
    }

    @Test
    void refusesToStartWithAWeakSecret() {
        assertThatThrownBy(() -> new JwtUtil("short")).isInstanceOf(IllegalStateException.class);
    }
}
