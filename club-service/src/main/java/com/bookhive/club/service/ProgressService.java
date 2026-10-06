package com.bookhive.club.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookhive.club.dto.ProgressResponse;
import com.bookhive.club.dto.UpdateProgressRequest;
import com.bookhive.club.entity.Book;
import com.bookhive.club.entity.Club;
import com.bookhive.club.entity.Progress;
import com.bookhive.club.exception.ForbiddenException;
import com.bookhive.club.exception.NotFoundException;
import com.bookhive.club.feign.UserDirectory;
import com.bookhive.club.repository.ClubRepository;
import com.bookhive.club.repository.MembershipRepository;
import com.bookhive.club.repository.ProgressRepository;
import com.bookhive.club.security.AuthenticatedUser;

@Service
public class ProgressService {

    private final ProgressRepository progressRepository;
    private final ClubRepository clubRepository;
    private final MembershipRepository membershipRepository;
    private final UserDirectory userDirectory;

    public ProgressService(ProgressRepository progressRepository,
                           ClubRepository clubRepository,
                           MembershipRepository membershipRepository,
                           UserDirectory userDirectory) {
        this.progressRepository = progressRepository;
        this.clubRepository = clubRepository;
        this.membershipRepository = membershipRepository;
        this.userDirectory = userDirectory;
    }

    @Transactional
    public ProgressResponse updateProgress(UUID clubId, UpdateProgressRequest request, AuthenticatedUser user) {
        requireMember(clubId, user.id());
        Club club = findClub(clubId);

        Book book = club.getCurrentBook();
        if (book == null) {
            throw new IllegalArgumentException("This club has no current book set");
        }
        Integer chapter = request == null ? null : request.currentChapter();
        if (chapter == null || chapter < 0 || chapter > book.getTotalChapters()) {
            throw new IllegalArgumentException("Chapter out of range for this book");
        }

        Progress progress = progressRepository
                .findByUserIdAndClubIdAndBookId(user.id(), clubId, book.getId())
                .orElseGet(() -> {
                    Progress created = new Progress();
                    created.setUserId(user.id());
                    created.setClub(club);
                    created.setBook(book);
                    return created;
                });
        progress.setCurrentChapter(chapter);
        progressRepository.save(progress);

        return toResponse(progress, book, user.name());
    }

    @Transactional(readOnly = true)
    public List<ProgressResponse> getClubProgress(UUID clubId, UUID userId) {
        requireMember(clubId, userId);
        Club club = findClub(clubId);
        Book book = club.getCurrentBook();
        if (book == null) {
            return List.of();
        }

        List<Progress> all = progressRepository.findByClubIdAndBookId(clubId, book.getId());
        Map<UUID, String> names = userDirectory.namesFor(all.stream().map(Progress::getUserId).toList());
        return all.stream()
                .map(p -> toResponse(p, book, names.getOrDefault(p.getUserId(), UserDirectory.UNKNOWN_USER)))
                .toList();
    }

    /** The caller's own progress, or null if they haven't logged any yet (or the club has no book). */
    @Transactional(readOnly = true)
    public ProgressResponse getMyProgress(UUID clubId, AuthenticatedUser user) {
        requireMember(clubId, user.id());
        Club club = findClub(clubId);
        Book book = club.getCurrentBook();
        if (book == null) {
            return null;
        }
        return progressRepository.findByUserIdAndClubIdAndBookId(user.id(), clubId, book.getId())
                .map(p -> toResponse(p, book, user.name()))
                .orElse(null);
    }

    private void requireMember(UUID clubId, UUID userId) {
        if (!membershipRepository.existsByUserIdAndClubId(userId, clubId)) {
            throw new ForbiddenException("Not a member of this club");
        }
    }

    private Club findClub(UUID clubId) {
        return clubRepository.findById(clubId).orElseThrow(() -> new NotFoundException("Club not found"));
    }

    private ProgressResponse toResponse(Progress progress, Book book, String userName) {
        return new ProgressResponse(
                progress.getId(),
                progress.getUserId(),
                userName,
                book.getId(),
                progress.getCurrentChapter(),
                book.getTotalChapters(),
                progress.getUpdatedAt());
    }
}
