package com.bookhive.club.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bookhive.club.dto.CreateReviewRequest;
import com.bookhive.club.dto.ReviewListResponse;
import com.bookhive.club.dto.ReviewResponse;
import com.bookhive.club.security.CurrentUser;
import com.bookhive.club.service.ReviewService;

@RestController
@RequestMapping("/api/books/{bookId}/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public ReviewResponse addReview(@PathVariable UUID bookId, @RequestBody CreateReviewRequest request) {
        return reviewService.addReview(bookId, request, CurrentUser.get());
    }

    @GetMapping
    public ReviewListResponse getReviews(@PathVariable UUID bookId,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "5") int size) {
        return reviewService.getReviews(bookId, page, size);
    }
}
