package com.bookhive.club;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.bookhive.club.entity.Book;
import com.bookhive.club.entity.Club;
import com.bookhive.club.entity.Comment;
import com.bookhive.club.entity.Membership;
import com.bookhive.club.entity.Progress;
import com.bookhive.club.entity.Review;
import com.bookhive.club.enums.ClubRole;
import com.bookhive.club.repository.BookRepository;
import com.bookhive.club.repository.ClubRepository;
import com.bookhive.club.repository.CommentRepository;
import com.bookhive.club.repository.MembershipRepository;
import com.bookhive.club.repository.ProgressRepository;
import com.bookhive.club.repository.ReviewRepository;

/**
 * Proves the derived and hand-written queries really work against Postgres - the mocks in the unit tests
 * can't tell us that. Each test rolls back, so they don't interfere with each other.
 */
@Testcontainers
@Transactional
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false"
})
class ClubRepositoriesIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private BookRepository bookRepository;
    @Autowired private ClubRepository clubRepository;
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private CommentRepository commentRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private ProgressRepository progressRepository;

    private Book newBook(String title) {
        Book book = new Book();
        book.setTitle(title);
        book.setAuthor("Author");
        book.setTotalChapters(10);
        return bookRepository.save(book);
    }

    private Club newClub(String name, Book currentBook) {
        Club club = new Club();
        club.setName(name);
        club.setCreatedById(UUID.randomUUID());
        club.setCurrentBook(currentBook);
        return clubRepository.save(club);
    }

    private void comment(Club club, UUID userId, int chapter, String text) {
        Comment comment = new Comment();
        comment.setClub(club);
        comment.setUserId(userId);
        comment.setChapterNumber(chapter);
        comment.setContent(text);
        commentRepository.save(comment);
    }

    private void review(Book book, UUID userId, int rating) {
        Review review = new Review();
        review.setBook(book);
        review.setUserId(userId);
        review.setRating(rating);
        reviewRepository.save(review);
    }

    @Test
    void clubsCanBeFoundByTheirCurrentBook() {
        Book dune = newBook("Dune");
        Book other = newBook("Other");
        Club club = newClub("Sci-Fi", dune);

        assertThat(clubRepository.existsByCurrentBookId(dune.getId())).isTrue();
        assertThat(clubRepository.existsByCurrentBookId(other.getId())).isFalse();
        assertThat(clubRepository.findByCurrentBookId(dune.getId())).extracting(Club::getId).containsExactly(club.getId());
    }

    @Test
    void participantsOfAChapterAreDistinctAndChapterSpecific() {
        Club club = newClub("Sci-Fi", null);
        UUID ann = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        comment(club, ann, 1, "one");
        comment(club, ann, 1, "two");
        comment(club, bob, 1, "three");
        comment(club, UUID.randomUUID(), 2, "different chapter");

        List<UUID> participants = commentRepository.findParticipantIds(club.getId(), 1);

        assertThat(participants).containsExactlyInAnyOrder(ann, bob);
    }

    @Test
    void commentsArePagedOldestFirst() {
        Club club = newClub("Sci-Fi", null);
        UUID ann = UUID.randomUUID();
        comment(club, ann, 1, "first");
        comment(club, ann, 1, "second");
        comment(club, ann, 1, "third");
        comment(club, ann, 2, "other chapter");

        Page<Comment> page = commentRepository.findByClubIdAndChapterNumber(
                club.getId(), 1, PageRequest.of(0, 2, Sort.by(Sort.Direction.ASC, "createdAt")));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(2);
    }

    @Test
    void averageRatingAndDuplicateChecksWork() {
        Book book = newBook("Dune");
        UUID ann = UUID.randomUUID();
        review(book, ann, 4);
        review(book, UUID.randomUUID(), 2);

        assertThat(reviewRepository.findAverageRating(book.getId())).isEqualTo(3.0);
        assertThat(reviewRepository.existsByBookIdAndUserId(book.getId(), ann)).isTrue();
        assertThat(reviewRepository.existsByBookIdAndUserId(book.getId(), UUID.randomUUID())).isFalse();
        assertThat(reviewRepository.findAverageRating(newBook("Unreviewed").getId())).isNull();
    }

    @Test
    void deletingByBookRemovesReviewsAndProgress() {
        Book book = newBook("Dune");
        Club club = newClub("Sci-Fi", book);
        review(book, UUID.randomUUID(), 5);
        Progress progress = new Progress();
        progress.setUserId(UUID.randomUUID());
        progress.setClub(club);
        progress.setBook(book);
        progress.setCurrentChapter(3);
        progressRepository.save(progress);

        reviewRepository.deleteByBookId(book.getId());
        progressRepository.deleteByBookId(book.getId());

        assertThat(reviewRepository.findByBookId(book.getId(), PageRequest.of(0, 10)).getTotalElements()).isZero();
        assertThat(progressRepository.findByClubIdAndBookId(club.getId(), book.getId())).isEmpty();
    }

    @Test
    void membershipQueriesWork() {
        Club club = newClub("Sci-Fi", null);
        UUID ann = UUID.randomUUID();
        Membership membership = new Membership();
        membership.setClub(club);
        membership.setUserId(ann);
        membership.setRoleInClub(ClubRole.ADMIN);
        membershipRepository.save(membership);

        assertThat(membershipRepository.existsByUserIdAndClubId(ann, club.getId())).isTrue();
        assertThat(membershipRepository.existsByUserIdAndClubId(UUID.randomUUID(), club.getId())).isFalse();
        assertThat(membershipRepository.findByUserIdAndClubId(ann, club.getId()))
                .get().extracting(Membership::getRoleInClub).isEqualTo(ClubRole.ADMIN);
        assertThat(membershipRepository.findByUserId(ann)).hasSize(1);
        assertThat(membershipRepository.findByClubId(club.getId())).hasSize(1);

        membershipRepository.deleteByClubId(club.getId());

        assertThat(membershipRepository.findByClubId(club.getId())).isEmpty();
    }
}
