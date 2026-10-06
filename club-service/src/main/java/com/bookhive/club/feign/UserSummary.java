package com.bookhive.club.feign;

import java.util.UUID;

/** What user-service tells us about a user: just enough to show a name. */
public record UserSummary(UUID id, String name) {}
