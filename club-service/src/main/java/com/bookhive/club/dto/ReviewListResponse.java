package com.bookhive.club.dto;

public record ReviewListResponse(PageResponse<ReviewResponse> reviews, Double averageRating) {}
