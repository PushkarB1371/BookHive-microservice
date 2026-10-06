package com.bookhive.club.dto;

import java.util.UUID;

public record ClubResponse(
        UUID id,
        String name,
        String description,
        String coverImageUrl,
        BookResponse currentBook,
        UUID createdBy,
        String createdByName,
        boolean isCurrentUserAdmin,
        boolean isCurrentUserMember
) {}
