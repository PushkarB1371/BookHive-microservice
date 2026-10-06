package com.bookhive.club.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.bookhive.club.TestData;
import com.bookhive.club.dto.CreateReviewRequest;
import com.bookhive.club.dto.ReviewListResponse;
import com.bookhive.club.dto.ReviewResponse;
import com.bookhive.club.entity.Book;
import com.bookhive.club.entity.Review;
import com.bookhive.club.enums.NotificationType;
import com.bookhive.club.feign.UserDirectory;
import com.bookhive.club.messaging.NotificationPublisher;
import com.bookhive.club.repository.ReviewRepository;
import com.bookhive.club.security.AuthenticatedUser;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private BookService bookService;
    @Mock private NotificationPublisher notificationPublisher;
    @Mock private UserDirectory userDirectory;

    @InjectMocks
    private ReviewService reviewService;

    private final AuthenticatedUser bob = TestData.member("Bob");

    @Test
    void ratingMustBeBetweenOneAndFive() {
        UUID bookId = UUID.randomUUID();

        assertThatThrownBy(() -> reviewService.addReview(bookId, new CreateReviewRequest(0, "bad"), bob))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("between 1 and 5");
        assertThatThrownBy(() -> reviewService.addReview(bookId, new CreateReviewRequest(6, "too good"), bob))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reviewService.addReview(bookId, new CreateReviewRequest(null, "?"), bob))
                .isInstanceOf(IllegalArgumentException.class);

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void youCannotReviewTheSameBookTwice() {
        Book book = TestData.book("Dune", 20);
        when(bookService.getBookEntity(book.getId())).thenReturn(book);
        when(reviewRepository.existsByBookIdAndUserId(book.getId(), bob.id())).thenReturn(true);

        assertThatThrownBy(() -> reviewService.addReview(book.getId(), new CreateReviewRequest(5, "again"), bob))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already reviewed");

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void aReviewIsSavedAndTheAdminWhoAddedTheBookIsNotified() {
        UUID adminId = UUID.randomUUID();
        Book book = TestData.book("Dune", 20);
        book.setAddedById(adminId);
        when(bookService.getBookEntity(book.getId())).thenReturn(book);
        when(reviewRepository.existsByBookIdAndUserId(book.getId(), bob.id())).thenReturn(false);

        ReviewResponse response = reviewService.addReview(book.getId(), new CreateReviewRequest(5, " Loved it "), bob);

        assertThat(response.rating()).isEqualTo(5);
        assertThat(response.reviewText()).isEqualTo("Loved it");
        assertThat(response.userName()).isEqualTo("Bob");
        verify(notificationPublisher).publish(
                eq(adminId), eq(NotificationType.NEW_REPLY), contains("Bob reviewed Dune (5 stars)"));
    }

    @Test
    void anAdminReviewingTheirOwnBookNotifiesNobody() {
        Book book = TestData.book("Dune", 20);
        book.setAddedById(bob.id());
        when(bookService.getBookEntity(book.getId())).thenReturn(book);
        when(reviewRepository.existsByBookIdAndUserId(book.getId(), bob.id())).thenReturn(false);

        reviewService.addReview(book.getId(), new CreateReviewRequest(4, null), bob);

        verifyNoInteractions(notificationPublisher);
    }

    @Test
    void booksWithoutARecordedAdminNotifyNobody() {
        Book legacy = TestData.book("Old Book", 10);     // addedById stays null
        when(bookService.getBookEntity(legacy.getId())).thenReturn(legacy);
        when(reviewRepository.existsByBookIdAndUserId(legacy.getId(), bob.id())).thenReturn(false);

        reviewService.addReview(legacy.getId(), new CreateReviewRequest(3, "ok"), bob);

        verifyNoInteractions(notificationPublisher);
    }

    @Test
    void reviewListCarriesAverageRatingPagingAndNames() {
        Book book = TestData.book("Dune", 20);
        UUID knownReader = UUID.randomUUID();
        UUID unknownReader = UUID.randomUUID();
        when(bookService.getBookEntity(book.getId())).thenReturn(book);
        when(reviewRepository.findByBookId(eq(book.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(
                        List.of(review(book, knownReader, 5), review(book, unknownReader, 4)),
                        PageRequest.of(0, 5), 12));
        when(reviewRepository.findAverageRating(book.getId())).thenReturn(4.5);
        when(userDirectory.namesFor(anyCollection())).thenReturn(Map.of(knownReader, "Kim"));

        ReviewListResponse response = reviewService.getReviews(book.getId(), 0, 5);

        assertThat(response.averageRating()).isEqualTo(4.5);
        assertThat(response.reviews().totalElements()).isEqualTo(12);
        assertThat(response.reviews().totalPages()).isEqualTo(3);
        assertThat(response.reviews().content()).extracting(ReviewResponse::userName)
                .containsExactly("Kim", UserDirectory.UNKNOWN_USER);
    }

    @Test
    void aBookWithNoReviewsHasNoAverage() {
        Book book = TestData.book("Dune", 20);
        when(bookService.getBookEntity(book.getId())).thenReturn(book);
        when(reviewRepository.findByBookId(eq(book.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 5), 0));
        when(reviewRepository.findAverageRating(book.getId())).thenReturn(null);

        ReviewListResponse response = reviewService.getReviews(book.getId(), 0, 5);

        assertThat(response.averageRating()).isNull();
        assertThat(response.reviews().content()).isEmpty();
    }

    private Review review(Book book, UUID userId, int rating) {
        Review review = new Review();
        review.setId(UUID.randomUUID());
        review.setBook(book);
        review.setUserId(userId);
        review.setRating(rating);
        return review;
    }
}
