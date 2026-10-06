package com.bookhive.club.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookhive.club.dto.CreateReviewRequest;
import com.bookhive.club.dto.PageResponse;
import com.bookhive.club.dto.ReviewListResponse;
import com.bookhive.club.dto.ReviewResponse;
import com.bookhive.club.entity.Book;
import com.bookhive.club.entity.Review;
import com.bookhive.club.enums.NotificationType;
import com.bookhive.club.feign.UserDirectory;
import com.bookhive.club.messaging.NotificationPublisher;
import com.bookhive.club.repository.ReviewRepository;
import com.bookhive.club.security.AuthenticatedUser;
import com.bookhive.club.util.PaginationUtil;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookService bookService;
    private final NotificationPublisher notificationPublisher;
    private final UserDirectory userDirectory;

    public ReviewService(ReviewRepository reviewRepository,
                         BookService bookService,
                         NotificationPublisher notificationPublisher,
                         UserDirectory userDirectory) {
        this.reviewRepository = reviewRepository;
        this.bookService = bookService;
        this.notificationPublisher = notificationPublisher;
        this.userDirectory = userDirectory;
    }

    @Transactional
    public ReviewResponse addReview(UUID bookId, CreateReviewRequest request, AuthenticatedUser user) {
        if (request == null || request.rating() == null || request.rating() < 1 || request.rating() > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
        Book book = bookService.getBookEntity(bookId);
        if (reviewRepository.existsByBookIdAndUserId(bookId, user.id())) {
            throw new IllegalArgumentException("You already reviewed this book");
        }

        Review review = new Review();
        review.setUserId(user.id());
        review.setBook(book);
        review.setRating(request.rating());
        review.setReviewText(request.reviewText() == null || request.reviewText().isBlank()
                ? null : request.reviewText().trim());
        reviewRepository.save(review);

        // Tell the admin who added the book (unless they are reviewing their own book).
        if (book.getAddedById() != null && !book.getAddedById().equals(user.id())) {
            notificationPublisher.publish(book.getAddedById(), NotificationType.NEW_REPLY,
                    user.name() + " reviewed " + book.getTitle() + " (" + request.rating() + " stars)");
        }

        return toResponse(review, user.name());
    }

    @Transactional(readOnly = true)
    public ReviewListResponse getReviews(UUID bookId, int page, int size) {
        bookService.getBookEntity(bookId);

        Pageable pageable = PageRequest.of(
                PaginationUtil.safePage(page), PaginationUtil.safeSize(size), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Review> result = reviewRepository.findByBookId(bookId, pageable);

        Map<UUID, String> names = userDirectory.namesFor(result.getContent().stream().map(Review::getUserId).toList());
        List<ReviewResponse> content = result.getContent().stream()
                .map(r -> toResponse(r, names.getOrDefault(r.getUserId(), UserDirectory.UNKNOWN_USER)))
                .toList();

        PageResponse<ReviewResponse> pageResponse = PageResponse.of(result, content);
        return new ReviewListResponse(pageResponse, reviewRepository.findAverageRating(bookId));
    }

    private ReviewResponse toResponse(Review review, String userName) {
        return new ReviewResponse(
                review.getId(),
                review.getUserId(),
                userName,
                review.getRating(),
                review.getReviewText(),
                review.getCreatedAt());
    }
}
