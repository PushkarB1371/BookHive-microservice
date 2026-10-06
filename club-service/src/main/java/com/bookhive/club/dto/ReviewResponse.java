package com.bookhive.club.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReviewResponse(
        UUID id,
        UUID userId,
        String userName,
        Integer rating,
        String reviewText,
        LocalDateTime createdAt
) {}
