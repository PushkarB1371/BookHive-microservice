package com.bookhive.club.security;

import java.util.UUID;

/** The identity carried inside a verified JWT. */
public record AuthenticatedUser(UUID id, String name, String email, String role) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
