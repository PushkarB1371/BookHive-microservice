package com.bookhive.club.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record CommentResponse(
        UUID id,
        UUID userId,
        String userName,
        Integer chapterNumber,
        String content,
        LocalDateTime createdAt
) {}
