package com.bookhive.club.dto;

public record BookSearchResult(
        String title,
        String author,
        String coverImageUrl,
        String description,
        String publishedDate,
        String category
) {}
