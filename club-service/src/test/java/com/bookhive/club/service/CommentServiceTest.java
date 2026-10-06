package com.bookhive.club.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import com.bookhive.club.dto.CommentResponse;
import com.bookhive.club.dto.CreateCommentRequest;
import com.bookhive.club.dto.PageResponse;
import com.bookhive.club.entity.Club;
import com.bookhive.club.entity.Comment;
import com.bookhive.club.enums.NotificationType;
import com.bookhive.club.exception.ForbiddenException;
import com.bookhive.club.feign.UserDirectory;
import com.bookhive.club.messaging.NotificationPublisher;
import com.bookhive.club.repository.ClubRepository;
import com.bookhive.club.repository.CommentRepository;
import com.bookhive.club.repository.MembershipRepository;
import com.bookhive.club.security.AuthenticatedUser;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock private CommentRepository commentRepository;
    @Mock private ClubRepository clubRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private NotificationPublisher notificationPublisher;
    @Mock private UserDirectory userDirectory;

    @InjectMocks
    private CommentService commentService;

    private final AuthenticatedUser ann = TestData.member("Ann");

    @Test
    void nonMembersCannotComment() {
        UUID clubId = UUID.randomUUID();
        when(membershipRepository.existsByUserIdAndClubId(ann.id(), clubId)).thenReturn(false);

        assertThatThrownBy(() -> commentService.addComment(clubId, new CreateCommentRequest(1, "hi"), ann))
                .isInstanceOf(ForbiddenException.class);

        verify(commentRepository, never()).save(any());
    }

    @Test
    void emptyCommentsAreRejected() {
        Club club = TestData.club("Sci-Fi", UUID.randomUUID());
        when(membershipRepository.existsByUserIdAndClubId(ann.id(), club.getId())).thenReturn(true);
        when(clubRepository.findById(club.getId())).thenReturn(Optional.of(club));

        assertThatThrownBy(() -> commentService.addComment(club.getId(), new CreateCommentRequest(1, "   "), ann))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void aChapterNumberIsRequired() {
        Club club = TestData.club("Sci-Fi", UUID.randomUUID());
        when(membershipRepository.existsByUserIdAndClubId(ann.id(), club.getId())).thenReturn(true);
        when(clubRepository.findById(club.getId())).thenReturn(Optional.of(club));

        assertThatThrownBy(() -> commentService.addComment(club.getId(), new CreateCommentRequest(null, "hi"), ann))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Chapter");
    }

    @Test
    void everyoneElseAlreadyTalkingInTheChapterIsNotified() {
        AuthenticatedUser bob = TestData.member("Bob");
        AuthenticatedUser cat = TestData.member("Cat");
        Club club = TestData.club("Sci-Fi", UUID.randomUUID());
        when(membershipRepository.existsByUserIdAndClubId(ann.id(), club.getId())).thenReturn(true);
        when(clubRepository.findById(club.getId())).thenReturn(Optional.of(club));
        when(commentRepository.findParticipantIds(club.getId(), 3)).thenReturn(List.of(ann.id(), bob.id(), cat.id()));

        CommentResponse response = commentService.addComment(club.getId(), new CreateCommentRequest(3, " Loved it "), ann);

        assertThat(response.content()).isEqualTo("Loved it");
        assertThat(response.userName()).isEqualTo("Ann");
        verify(notificationPublisher).publish(
                eq(bob.id()), eq(NotificationType.NEW_REPLY), contains("Ann commented on Chapter 3 in Sci-Fi"));
        verify(notificationPublisher).publish(
                eq(cat.id()), eq(NotificationType.NEW_REPLY), contains("Ann commented on Chapter 3 in Sci-Fi"));
        verify(notificationPublisher, never()).publish(eq(ann.id()), any(), anyString());
    }

    @Test
    void theFirstCommenterInAChapterNotifiesNobody() {
        Club club = TestData.club("Sci-Fi", UUID.randomUUID());
        when(membershipRepository.existsByUserIdAndClubId(ann.id(), club.getId())).thenReturn(true);
        when(clubRepository.findById(club.getId())).thenReturn(Optional.of(club));
        when(commentRepository.findParticipantIds(club.getId(), 1)).thenReturn(List.of());

        commentService.addComment(club.getId(), new CreateCommentRequest(1, "First!"), ann);

        verifyNoInteractions(notificationPublisher);
    }

    @Test
    void commentsArePagedAndEachGetsItsAuthorsName() {
        UUID clubId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        UUID ghostId = UUID.randomUUID();
        Club club = TestData.club("Sci-Fi", UUID.randomUUID());
        Comment first = comment(club, bobId, "Great twist");
        Comment second = comment(club, ghostId, "Agreed");
        when(membershipRepository.existsByUserIdAndClubId(ann.id(), clubId)).thenReturn(true);
        when(commentRepository.findByClubIdAndChapterNumber(eq(clubId), eq(2), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second), PageRequest.of(0, 10), 25));
        when(userDirectory.namesFor(anyCollection())).thenReturn(Map.of(bobId, "Bob"));

        PageResponse<CommentResponse> page = commentService.getComments(clubId, 2, 0, 10, ann.id());

        assertThat(page.totalElements()).isEqualTo(25);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.content()).extracting(CommentResponse::userName)
                .containsExactly("Bob", UserDirectory.UNKNOWN_USER);
    }

    @Test
    void nonMembersCannotReadTheDiscussion() {
        UUID clubId = UUID.randomUUID();
        when(membershipRepository.existsByUserIdAndClubId(ann.id(), clubId)).thenReturn(false);

        assertThatThrownBy(() -> commentService.getComments(clubId, 1, 0, 10, ann.id()))
                .isInstanceOf(ForbiddenException.class);
    }

    private Comment comment(Club club, UUID userId, String content) {
        Comment comment = new Comment();
        comment.setId(UUID.randomUUID());
        comment.setClub(club);
        comment.setUserId(userId);
        comment.setChapterNumber(2);
        comment.setContent(content);
        return comment;
    }
}
