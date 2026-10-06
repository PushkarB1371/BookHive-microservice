package com.bookhive.club.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookhive.club.dto.ClubResponse;
import com.bookhive.club.dto.CreateClubRequest;
import com.bookhive.club.dto.PageResponse;
import com.bookhive.club.entity.Book;
import com.bookhive.club.entity.Club;
import com.bookhive.club.entity.Membership;
import com.bookhive.club.enums.ClubRole;
import com.bookhive.club.enums.NotificationType;
import com.bookhive.club.exception.ForbiddenException;
import com.bookhive.club.exception.NotFoundException;
import com.bookhive.club.feign.UserDirectory;
import com.bookhive.club.mapper.BookMapper;
import com.bookhive.club.messaging.NotificationPublisher;
import com.bookhive.club.repository.ClubRepository;
import com.bookhive.club.repository.CommentRepository;
import com.bookhive.club.repository.MembershipRepository;
import com.bookhive.club.repository.ProgressRepository;
import com.bookhive.club.security.AuthenticatedUser;
import com.bookhive.club.util.PaginationUtil;

@Service
public class ClubService {

    public static final String MINE = "mine";
    public static final String JOINED = "joined";
    public static final String DISCOVER = "discover";
    private static final Set<String> LIST_TYPES = Set.of(MINE, JOINED, DISCOVER);
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    private final ClubRepository clubRepository;
    private final MembershipRepository membershipRepository;
    private final CommentRepository commentRepository;
    private final ProgressRepository progressRepository;
    private final BookService bookService;
    private final NotificationPublisher notificationPublisher;
    private final UserDirectory userDirectory;

    public ClubService(ClubRepository clubRepository,
                       MembershipRepository membershipRepository,
                       CommentRepository commentRepository,
                       ProgressRepository progressRepository,
                       BookService bookService,
                       NotificationPublisher notificationPublisher,
                       UserDirectory userDirectory) {
        this.clubRepository = clubRepository;
        this.membershipRepository = membershipRepository;
        this.commentRepository = commentRepository;
        this.progressRepository = progressRepository;
        this.bookService = bookService;
        this.notificationPublisher = notificationPublisher;
        this.userDirectory = userDirectory;
    }

    @Transactional
    public ClubResponse createClub(CreateClubRequest request, AuthenticatedUser user) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Club name is required");
        }

        Club club = new Club();
        club.setName(request.name().trim());
        club.setDescription(blankToNull(request.description()));
        club.setCoverImageUrl(blankToNull(request.coverImageUrl()));
        club.setCreatedById(user.id());
        clubRepository.save(club);

        Membership membership = new Membership();
        membership.setClub(club);
        membership.setUserId(user.id());
        membership.setRoleInClub(ClubRole.ADMIN);
        membershipRepository.save(membership);

        return toResponse(club, membership, Map.of(user.id(), user.name()));
    }

    @Transactional(readOnly = true)
    public List<ClubResponse> getAllClubs(UUID userId) {
        return toResponses(clubRepository.findAll(NEWEST_FIRST), userId);
    }

    @Transactional(readOnly = true)
    public ClubResponse getClubById(UUID clubId, UUID userId) {
        Club club = findClub(clubId);
        Membership membership = membershipRepository.findByUserIdAndClubId(userId, clubId).orElse(null);
        return toResponse(club, membership, userDirectory.namesFor(List.of(club.getCreatedById())));
    }

    @Transactional
    public ClubResponse setCurrentBook(UUID clubId, UUID bookId, AuthenticatedUser user) {
        Club club = findClub(clubId);
        Membership membership = requireClubAdmin(clubId, user.id());

        Book book = bookService.getBookEntity(bookId);
        club.setCurrentBook(book);
        clubRepository.save(club);

        String message = club.getName() + " is now reading " + book.getTitle();
        for (Membership member : membershipRepository.findByClubId(clubId)) {
            if (!member.getUserId().equals(user.id())) {
                notificationPublisher.publish(member.getUserId(), NotificationType.CHAPTER_UNLOCK, message);
            }
        }

        return toResponse(club, membership, creatorNames(club, user));
    }

    @Transactional
    public void joinClub(UUID clubId, AuthenticatedUser user) {
        if (membershipRepository.existsByUserIdAndClubId(user.id(), clubId)) {
            throw new IllegalArgumentException("Already a member of this club");
        }
        Club club = findClub(clubId);

        Membership membership = new Membership();
        membership.setClub(club);
        membership.setUserId(user.id());
        membership.setRoleInClub(ClubRole.MEMBER);
        membershipRepository.save(membership);

        if (!club.getCreatedById().equals(user.id())) {
            notificationPublisher.publish(club.getCreatedById(), NotificationType.NEW_REPLY,
                    user.name() + " joined " + club.getName());
        }
    }

    @Transactional
    public void deleteClub(UUID clubId, AuthenticatedUser user) {
        Club club = findClub(clubId);
        if (!club.getCreatedById().equals(user.id())) {
            throw new ForbiddenException("Only the club creator can delete this club");
        }
        commentRepository.deleteByClubId(clubId);
        progressRepository.deleteByClubId(clubId);
        membershipRepository.deleteByClubId(clubId);
        clubRepository.delete(club);
    }

    /** Clubs split into the three tabs of the clubs page: mine, joined and discover. */
    @Transactional(readOnly = true)
    public Map<String, List<ClubResponse>> getClubsGroupedForUser(UUID userId) {
        List<Club> clubs = clubRepository.findAll(NEWEST_FIRST);
        Map<UUID, Membership> mine = membershipsByClub(userId);
        Map<UUID, String> names = userDirectory.namesFor(
                clubs.stream().map(Club::getCreatedById).collect(Collectors.toSet()));

        List<ClubResponse> created = new ArrayList<>();
        List<ClubResponse> joined = new ArrayList<>();
        List<ClubResponse> discover = new ArrayList<>();
        for (Club club : clubs) {
            Membership membership = mine.get(club.getId());
            ClubResponse response = toResponse(club, membership, names);
            if (club.getCreatedById().equals(userId)) {
                created.add(response);
            } else if (membership != null) {
                joined.add(response);
            } else {
                discover.add(response);
            }
        }

        Map<String, List<ClubResponse>> result = new LinkedHashMap<>();
        result.put(MINE, created);
        result.put(JOINED, joined);
        result.put(DISCOVER, discover);
        return result;
    }

    @Transactional(readOnly = true)
    public PageResponse<ClubResponse> getGroupedPaged(UUID userId, String type, int page, int size) {
        if (type == null || !LIST_TYPES.contains(type)) {
            throw new IllegalArgumentException("type must be one of: mine, joined, discover");
        }
        return PaginationUtil.paginate(getClubsGroupedForUser(userId).get(type), page, size);
    }

    @Transactional(readOnly = true)
    public List<ClubResponse> getClubsReadingBook(UUID bookId, UUID userId) {
        return toResponses(clubRepository.findByCurrentBookId(bookId), userId);
    }

    // ------------------------------------------------------------------ helpers

    private Club findClub(UUID clubId) {
        return clubRepository.findById(clubId).orElseThrow(() -> new NotFoundException("Club not found"));
    }

    private Membership requireClubAdmin(UUID clubId, UUID userId) {
        Membership membership = membershipRepository.findByUserIdAndClubId(userId, clubId)
                .orElseThrow(() -> new ForbiddenException("Not a member of this club"));
        if (membership.getRoleInClub() != ClubRole.ADMIN) {
            throw new ForbiddenException("Only club admins can do this");
        }
        return membership;
    }

    private Map<UUID, Membership> membershipsByClub(UUID userId) {
        return membershipRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(m -> m.getClub().getId(), Function.identity(), (a, b) -> a));
    }

    private List<ClubResponse> toResponses(List<Club> clubs, UUID userId) {
        Map<UUID, Membership> mine = membershipsByClub(userId);
        Map<UUID, String> names = userDirectory.namesFor(
                clubs.stream().map(Club::getCreatedById).collect(Collectors.toSet()));
        return clubs.stream().map(c -> toResponse(c, mine.get(c.getId()), names)).toList();
    }

    private Map<UUID, String> creatorNames(Club club, AuthenticatedUser current) {
        if (club.getCreatedById().equals(current.id())) {
            return Map.of(current.id(), current.name());
        }
        return userDirectory.namesFor(List.of(club.getCreatedById()));
    }

    private ClubResponse toResponse(Club club, Membership membership, Map<UUID, String> names) {
        boolean member = membership != null;
        boolean admin = member && membership.getRoleInClub() == ClubRole.ADMIN;
        return new ClubResponse(
                club.getId(),
                club.getName(),
                club.getDescription(),
                club.getCoverImageUrl(),
                club.getCurrentBook() == null ? null : BookMapper.toResponse(club.getCurrentBook()),
                club.getCreatedById(),
                names.getOrDefault(club.getCreatedById(), UserDirectory.UNKNOWN_USER),
                admin,
                member);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
