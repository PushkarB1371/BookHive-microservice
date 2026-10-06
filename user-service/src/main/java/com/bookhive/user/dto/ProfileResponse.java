package com.bookhive.user.dto;

import java.util.UUID;

public record ProfileResponse(UUID id, String name, String email, String role) {}
