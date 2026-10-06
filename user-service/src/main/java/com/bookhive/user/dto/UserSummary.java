package com.bookhive.user.dto;

import java.util.UUID;

/** Minimal public view of a user, returned to other services (no email, no password). */
public record UserSummary(UUID id, String name) {}
