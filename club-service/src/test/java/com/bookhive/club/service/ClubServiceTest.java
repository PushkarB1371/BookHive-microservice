package com.bookhive.club.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import com.bookhive.club.TestData;
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
import com.bookhive.club.messaging.NotificationPublisher;
import com.bookhive.club.repository.ClubRepository;
import com.bookhive.club.repository.CommentRepository;
import com.bookhive.club.repository.MembershipRepository;
import com.bookhive.club.repository.ProgressRepository;
import com.bookhive.club.security.AuthenticatedUser;

@ExtendWith(MockitoExtension.class)
class ClubServiceTest {

    @Mock private ClubRepository clubRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private ProgressRepository progressRepository;
    @Mock private BookService bookService;
    @Mock private NotificationPublisher notificationPublisher;
    @Mock private UserDirectory userDirectory;

    @InjectMocks
    private ClubService clubService;

    @Test
    void creatingAClubMakesTheCreatorItsAdminMember() {
        AuthenticatedUser ann = TestData.member("Ann");

        ClubResponse response = clubService.createClub(new CreateClubRequest(" Sci-Fi Circle ", "We read sci-fi", ""), ann);

        assertThat(response.name()).isEqualTo("Sci-Fi Circle");
        assertThat(response.coverImageUrl()).isNull();
        assertThat(response.createdByName()).isEqualTo("Ann");
        assertThat(response.isCurrentUserAdmin()).isTrue();
        assertThat(response.isCurrentUserMember()).isTrue();

        ArgumentCaptor<Membership> membership = ArgumentCaptor.forClass(Membership.class);
        verify(membershipRepository).save(membership.capture());
        assertThat(membership.getValue().getUserId()).isEqualTo(ann.id());
        assertThat(membership.getValue().getRoleInClub()).isEqualTo(ClubRole.ADMIN);
        verifyNoInteractions(userDirectory);     // the creator's name comes from their own token
    }

    @Test
    void aClubNeedsAName() {
        assertThatThrownBy(() -> clubService.createClub(new CreateClubRequest("  ", null, null), TestData.member("Ann")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void joiningAddsAMemberAndTellsTheClubCreator() {
        UUID creator = UUID.randomUUID();
        Club club = TestData.club("Sci-Fi Circle", creator);
        AuthenticatedUser bob = TestData.member("Bob");
        when(membershipRepository.existsByUserIdAndClubId(bob.id(), club.getId())).thenReturn(false);
        when(clubRepository.findById(club.getId())).thenReturn(Optional.of(club));

        clubService.joinClub(club.getId(), bob);

        ArgumentCaptor<Membership> membership = ArgumentCaptor.forClass(Membership.class);
        verify(membershipRepository).save(membership.capture());
        assertThat(membership.getValue().getRoleInClub()).isEqualTo(ClubRole.MEMBER);
        verify(notificationPublisher).publish(
                eq(creator), eq(NotificationType.NEW_REPLY), contains("Bob joined Sci-Fi Circle"));
    }

    @Test
    void joiningTwiceIsRejected() {
        AuthenticatedUser bob = TestData.member("Bob");
        UUID clubId = UUID.randomUUID();
        when(membershipRepository.existsByUserIdAndClubId(bob.id(), clubId)).thenReturn(true);

        assertThatThrownBy(() -> clubService.joinClub(clubId, bob))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Already a member");

        verify(membershipRepository, never()).save(any());
        verifyNoInteractions(notificationPublisher);
    }

    @Test
    void joiningAClubThatDoesNotExistIsNotFound() {
        AuthenticatedUser bob = TestData.member("Bob");
        UUID clubId = UUID.randomUUID();
        when(membershipRepository.existsByUserIdAndClubId(bob.id(), clubId)).thenReturn(false);
        when(clubRepository.findById(clubId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clubService.joinClub(clubId, bob)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void onlyTheCreatorCanDeleteAClub() {
        Club club = TestData.club("Sci-Fi Circle", UUID.randomUUID());
        when(clubRepository.findById(club.getId())).thenReturn(Optional.of(club));

        assertThatThrownBy(() -> clubService.deleteClub(club.getId(), TestData.member("Bob")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("creator");

        verify(clubRepository, never()).delete(any());
    }

    @Test
    void deletingAClubRemovesItsDependentRowsBeforeTheClub() {
        AuthenticatedUser ann = TestData.member("Ann");
        Club club = TestData.club("Sci-Fi Circle", ann.id());
        when(clubRepository.findById(club.getId())).thenReturn(Optional.of(club));

        clubService.deleteClub(club.getId(), ann);

        InOrder order = inOrder(commentRepository, progressRepository, membershipRepository, clubRepository);
        order.verify(commentRepository).deleteByClubId(club.getId());
        order.verify(progressRepository).deleteByClubId(club.getId());
        order.verify(membershipRepository).deleteByClubId(club.getId());
        order.verify(clubRepository).delete(club);
    }

    @Test
    void aPlainMemberCannotChooseTheClubsBook() {
        AuthenticatedUser bob = TestData.member("Bob");
        Club club = TestData.club("Sci-Fi Circle", UUID.randomUUID());
        when(clubRepository.findById(club.getId())).thenReturn(Optional.of(club));
        when(membershipRepository.findByUserIdAndClubId(bob.id(), club.getId()))
                .thenReturn(Optional.of(TestData.membership(club, bob.id(), ClubRole.MEMBER)));

        assertThatThrownBy(() -> clubService.setCurrentBook(club.getId(), UUID.randomUUID(), bob))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("club admins");

        verifyNoInteractions(notificationPublisher);
    }

    @Test
    void someoneOutsideTheClubCannotChooseItsBook() {
        AuthenticatedUser outsider = TestData.member("Zed");
        Club club = TestData.club("Sci-Fi Circle", UUID.randomUUID());
        when(clubRepository.findById(club.getId())).thenReturn(Optional.of(club));
        when(membershipRepository.findByUserIdAndClubId(outsider.id(), club.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clubService.setCurrentBook(club.getId(), UUID.randomUUID(), outsider))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void choosingABookTellsEveryoneExceptTheAdmin() {
        AuthenticatedUser ann = TestData.member("Ann");          // club admin (creator)
        AuthenticatedUser bob = TestData.member("Bob");
        AuthenticatedUser cat = TestData.member("Cat");
        Club club = TestData.club("Sci-Fi Circle", ann.id());
        Book dune = TestData.book("Dune", 20);
        Membership annMembership = TestData.membership(club, ann.id(), ClubRole.ADMIN);

        when(clubRepository.findById(club.getId())).thenReturn(Optional.of(club));
        when(membershipRepository.findByUserIdAndClubId(ann.id(), club.getId())).thenReturn(Optional.of(annMembership));
        when(bookService.getBookEntity(dune.getId())).thenReturn(dune);
        when(membershipRepository.findByClubId(club.getId())).thenReturn(List.of(
                annMembership,
                TestData.membership(club, bob.id(), ClubRole.MEMBER),
                TestData.membership(club, cat.id(), ClubRole.MEMBER)));

        ClubResponse response = clubService.setCurrentBook(club.getId(), dune.getId(), ann);

        assertThat(response.currentBook().title()).isEqualTo("Dune");
        assertThat(response.createdByName()).isEqualTo("Ann");
        verify(clubRepository).save(club);
        verify(notificationPublisher).publish(eq(bob.id()), eq(NotificationType.CHAPTER_UNLOCK), contains("Dune"));
        verify(notificationPublisher).publish(eq(cat.id()), eq(NotificationType.CHAPTER_UNLOCK), contains("Dune"));
        verify(notificationPublisher, never()).publish(eq(ann.id()), any(), anyString());
    }

    @Test
    void clubsAreSplitIntoMineJoinedAndDiscover() {
        UUID me = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Club mine = TestData.club("Mine", me);
        Club joined = TestData.club("Joined", other);
        Club discover = TestData.club("Discover", other);
        when(clubRepository.findAll(any(Sort.class))).thenReturn(List.of(mine, joined, discover));
        when(membershipRepository.findByUserId(me)).thenReturn(List.of(
                TestData.membership(mine, me, ClubRole.ADMIN),
                TestData.membership(joined, me, ClubRole.MEMBER)));
        when(userDirectory.namesFor(anyCollection())).thenReturn(Map.of(me, "Me", other, "Olivia"));

        Map<String, List<ClubResponse>> groups = clubService.getClubsGroupedForUser(me);

        assertThat(groups.get("mine")).extracting(ClubResponse::name).containsExactly("Mine");
        assertThat(groups.get("joined")).extracting(ClubResponse::name).containsExactly("Joined");
        assertThat(groups.get("discover")).extracting(ClubResponse::name).containsExactly("Discover");

        ClubResponse joinedResponse = groups.get("joined").get(0);
        assertThat(joinedResponse.createdByName()).isEqualTo("Olivia");
        assertThat(joinedResponse.isCurrentUserMember()).isTrue();
        assertThat(joinedResponse.isCurrentUserAdmin()).isFalse();

        ClubResponse discoverResponse = groups.get("discover").get(0);
        assertThat(discoverResponse.isCurrentUserMember()).isFalse();
    }

    @Test
    void namesThatCannotBeResolvedShowAsAPlaceholder() {
        UUID me = UUID.randomUUID();
        Club club = TestData.club("Discover", UUID.randomUUID());
        when(clubRepository.findAll(any(Sort.class))).thenReturn(List.of(club));
        when(membershipRepository.findByUserId(me)).thenReturn(List.of());
        when(userDirectory.namesFor(anyCollection())).thenReturn(Map.of());

        Map<String, List<ClubResponse>> groups = clubService.getClubsGroupedForUser(me);

        assertThat(groups.get("discover").get(0).createdByName()).isEqualTo(UserDirectory.UNKNOWN_USER);
    }

    @Test
    void listsArePagedPerTab() {
        UUID me = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        List<Club> many = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            many.add(TestData.club("Club " + i, other));
        }
        when(clubRepository.findAll(any(Sort.class))).thenReturn(many);
        when(membershipRepository.findByUserId(me)).thenReturn(List.of());
        when(userDirectory.namesFor(anyCollection())).thenReturn(Map.of(other, "Olivia"));

        PageResponse<ClubResponse> page = clubService.getGroupedPaged(me, "discover", 1, 6);

        assertThat(page.content()).hasSize(6);
        assertThat(page.totalElements()).isEqualTo(14);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    void unknownTabNameIsRejected() {
        assertThatThrownBy(() -> clubService.getGroupedPaged(UUID.randomUUID(), "everything", 0, 6))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mine, joined, discover");
    }
}
