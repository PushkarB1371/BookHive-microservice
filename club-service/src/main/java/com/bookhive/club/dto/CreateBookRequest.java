package com.bookhive.club.dto;

public record CreateBookRequest(
        String title,
        String author,
        Integer totalChapters,
        String coverImageUrl,
        String description,
        String category,
        String publishedDate
) {}
