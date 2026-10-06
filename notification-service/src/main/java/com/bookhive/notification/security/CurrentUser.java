package com.bookhive.notification.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static AuthenticatedUser get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new IllegalStateException("No authenticated user in the security context");
    }

    public static UUID id() {
        return get().id();
    }

    /**
     * The caller's raw JWT as an Authorization header value, or null outside a request.
     * Read on the request thread and passed explicitly to Feign (circuit breakers run calls on another thread).
     */
    public static String bearerToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getCredentials() instanceof String token && !token.isBlank()) {
            return "Bearer " + token;
        }
        return null;
    }
}
