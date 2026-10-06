package com.bookhive.club.dto;

import java.util.UUID;

public record BookResponse(
        UUID id,
        String title,
        String author,
        Integer totalChapters,
        String coverImageUrl,
        String description,
        String category,
        String publishedDate
) {}
