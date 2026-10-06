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

import com.bookhive.club.dto.CommentResponse;
import com.bookhive.club.dto.CreateCommentRequest;
import com.bookhive.club.dto.PageResponse;
import com.bookhive.club.entity.Club;
import com.bookhive.club.entity.Comment;
import com.bookhive.club.enums.NotificationType;
import com.bookhive.club.exception.ForbiddenException;
import com.bookhive.club.exception.NotFoundException;
import com.bookhive.club.feign.UserDirectory;
import com.bookhive.club.messaging.NotificationPublisher;
import com.bookhive.club.repository.ClubRepository;
import com.bookhive.club.repository.CommentRepository;
import com.bookhive.club.repository.MembershipRepository;
import com.bookhive.club.security.AuthenticatedUser;
import com.bookhive.club.util.PaginationUtil;

@Service
public class CommentService {

    static final int MAX_COMMENT_LENGTH = 4000;

    private final CommentRepository commentRepository;
    private final ClubRepository clubRepository;
    private final MembershipRepository membershipRepository;
    private final NotificationPublisher notificationPublisher;
    private final UserDirectory userDirectory;

    public CommentService(CommentRepository commentRepository,
                          ClubRepository clubRepository,
                          MembershipRepository membershipRepository,
                          NotificationPublisher notificationPublisher,
                          UserDirectory userDirectory) {
        this.commentRepository = commentRepository;
        this.clubRepository = clubRepository;
        this.membershipRepository = membershipRepository;
        this.notificationPublisher = notificationPublisher;
        this.userDirectory = userDirectory;
    }

    @Transactional
    public CommentResponse addComment(UUID clubId, CreateCommentRequest request, AuthenticatedUser user) {
        requireMember(clubId, user.id());
        Club club = clubRepository.findById(clubId).orElseThrow(() -> new NotFoundException("Club not found"));

        if (request == null || request.content() == null || request.content().isBlank()) {
            throw new IllegalArgumentException("Comment cannot be empty");
        }
        if (request.content().length() > MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException("Comment is too long (max " + MAX_COMMENT_LENGTH + " characters)");
        }
        if (request.chapterNumber() == null || request.chapterNumber() < 1) {
            throw new IllegalArgumentException("Chapter number is required");
        }

        // Everyone already talking in this chapter gets told about the new reply (looked up before we save).
        List<UUID> participants = commentRepository.findParticipantIds(clubId, request.chapterNumber());

        Comment comment = new Comment();
        comment.setUserId(user.id());
        comment.setClub(club);
        comment.setChapterNumber(request.chapterNumber());
        comment.setContent(request.content().trim());
        commentRepository.save(comment);

        String message = user.name() + " commented on Chapter " + request.chapterNumber() + " in " + club.getName();
        participants.stream()
                .filter(id -> !id.equals(user.id()))
                .forEach(id -> notificationPublisher.publish(id, NotificationType.NEW_REPLY, message));

        return toResponse(comment, user.name());
    }

    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> getComments(UUID clubId, Integer chapter, int page, int size, UUID userId) {
        requireMember(clubId, userId);
        if (chapter == null || chapter < 1) {
            throw new IllegalArgumentException("Chapter number is required");
        }

        Pageable pageable = PageRequest.of(
                PaginationUtil.safePage(page), PaginationUtil.safeSize(size), Sort.by(Sort.Direction.ASC, "createdAt"));
        Page<Comment> result = commentRepository.findByClubIdAndChapterNumber(clubId, chapter, pageable);

        Map<UUID, String> names = userDirectory.namesFor(result.getContent().stream().map(Comment::getUserId).toList());
        List<CommentResponse> content = result.getContent().stream()
                .map(c -> toResponse(c, names.getOrDefault(c.getUserId(), UserDirectory.UNKNOWN_USER)))
                .toList();
        return PageResponse.of(result, content);
    }

    private void requireMember(UUID clubId, UUID userId) {
        if (!membershipRepository.existsByUserIdAndClubId(userId, clubId)) {
            throw new ForbiddenException("Not a member of this club");
        }
    }

    private CommentResponse toResponse(Comment comment, String userName) {
        return new CommentResponse(
                comment.getId(),
                comment.getUserId(),
                userName,
                comment.getChapterNumber(),
                comment.getContent(),
                comment.getCreatedAt());
    }
}
