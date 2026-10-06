package com.bookhive.club.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProgressResponse(
        UUID id,
        UUID userId,
        String userName,
        UUID bookId,
        Integer currentChapter,
        Integer totalChapters,
        LocalDateTime updatedAt
) {}
