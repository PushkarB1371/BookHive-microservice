package com.bookhive.club.security;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** Verifies tokens issued by user-service (same shared secret). This service never creates tokens. */
@Component
public class JwtUtil {

    private final SecretKey key;

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("jwt.secret must be at least 32 characters long");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public Optional<AuthenticatedUser> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            UUID id = UUID.fromString(claims.getSubject());
            String name = claims.get("name", String.class);
            String email = claims.get("email", String.class);
            String role = claims.get("role", String.class);
            return Optional.of(new AuthenticatedUser(
                    id,
                    name == null ? "" : name,
                    email == null ? "" : email,
                    role == null ? "MEMBER" : role));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
