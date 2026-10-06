package com.bookhive.user.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class JwtUtilTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 60_000);

    @Test
    void generatedTokenCarriesTheUsersIdentity() {
        UUID id = UUID.randomUUID();

        String token = jwtUtil.generateToken(id, "Ann", "ann@example.com", "ADMIN");
        AuthenticatedUser user = jwtUtil.parse(token).orElseThrow();

        assertThat(user.id()).isEqualTo(id);
        assertThat(user.name()).isEqualTo("Ann");
        assertThat(user.email()).isEqualTo("ann@example.com");
        assertThat(user.role()).isEqualTo("ADMIN");
        assertThat(user.isAdmin()).isTrue();
    }

    @Test
    void memberIsNotAdmin() {
        String token = jwtUtil.generateToken(UUID.randomUUID(), "Bob", "bob@example.com", "MEMBER");

        assertThat(jwtUtil.parse(token).orElseThrow().isAdmin()).isFalse();
    }

    @Test
    void tamperedPayloadIsRejected() {
        String token = jwtUtil.generateToken(UUID.randomUUID(), "Ann", "ann@example.com", "MEMBER");
        String[] parts = token.split("\\.");
        String forged = parts[0] + "." + parts[1].substring(0, parts[1].length() - 4) + "AAAA." + parts[2];

        assertThat(jwtUtil.parse(forged)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtUtil other = new JwtUtil("a-completely-different-secret-value-9876543210", 60_000);
        String token = other.generateToken(UUID.randomUUID(), "Eve", "eve@example.com", "ADMIN");

        assertThat(jwtUtil.parse(token)).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() {
        JwtUtil alreadyExpired = new JwtUtil(SECRET, -1_000);
        String token = alreadyExpired.generateToken(UUID.randomUUID(), "Ann", "ann@example.com", "MEMBER");

        assertThat(jwtUtil.parse(token)).isEmpty();
    }

    @Test
    void garbageIsRejected() {
        assertThat(jwtUtil.parse("not-a-jwt")).isEmpty();
        assertThat(jwtUtil.parse("")).isEmpty();
    }

    @Test
    void secretShorterThan32CharactersIsRefused() {
        assertThatThrownBy(() -> new JwtUtil("too-short", 1_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }
}
