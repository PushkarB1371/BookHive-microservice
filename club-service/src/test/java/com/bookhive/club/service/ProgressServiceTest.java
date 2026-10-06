package com.bookhive.club.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bookhive.club.TestData;
import com.bookhive.club.dto.ProgressResponse;
import com.bookhive.club.dto.UpdateProgressRequest;
import com.bookhive.club.entity.Book;
import com.bookhive.club.entity.Club;
import com.bookhive.club.entity.Progress;
import com.bookhive.club.exception.ForbiddenException;
import com.bookhive.club.feign.UserDirectory;
import com.bookhive.club.repository.ClubRepository;
import com.bookhive.club.repository.MembershipRepository;
import com.bookhive.club.repository.ProgressRepository;
import com.bookhive.club.security.AuthenticatedUser;

@ExtendWith(MockitoExtension.class)
class ProgressServiceTest {

    @Mock private ProgressRepository progressRepository;
    @Mock private ClubRepository clubRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private UserDirectory userDirectory;

    @InjectMocks
    private ProgressService progressService;

    private final AuthenticatedUser ann = TestData.member("Ann");

    private Club clubReading(Book book) {
        Club club = TestData.club("Sci-Fi", UUID.randomUUID());
        club.setCurrentBook(book);
        when(membershipRepository.existsByUserIdAndClubId(ann.id(), club.getId())).thenReturn(true);
        when(clubRepository.findById(club.getId())).thenReturn(Optional.of(club));
        return club;
    }

    @Test
    void nonMembersCannotLogProgress() {
        UUID clubId = UUID.randomUUID();
        when(membershipRepository.existsByUserIdAndClubId(ann.id(), clubId)).thenReturn(false);

        assertThatThrownBy(() -> progressService.updateProgress(clubId, new UpdateProgressRequest(3), ann))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void aClubWithoutABookHasNothingToTrack() {
        Club club = clubReading(null);

        assertThatThrownBy(() -> progressService.updateProgress(club.getId(), new UpdateProgressRequest(3), ann))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no current book");
    }

    @Test
    void theChapterMustBeWithinTheBook() {
        Club club = clubReading(TestData.book("Dune", 20));

        assertThatThrownBy(() -> progressService.updateProgress(club.getId(), new UpdateProgressRequest(21), ann))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("out of range");
        assertThatThrownBy(() -> progressService.updateProgress(club.getId(), new UpdateProgressRequest(-1), ann))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> progressService.updateProgress(club.getId(), new UpdateProgressRequest(null), ann))
                .isInstanceOf(IllegalArgumentException.class);

        verify(progressRepository, never()).save(any());
    }

    @Test
    void firstUpdateCreatesTheProgressRow() {
        Book dune = TestData.book("Dune", 20);
        Club club = clubReading(dune);
        when(progressRepository.findByUserIdAndClubIdAndBookId(ann.id(), club.getId(), dune.getId()))
                .thenReturn(Optional.empty());

        ProgressResponse response = progressService.updateProgress(club.getId(), new UpdateProgressRequest(5), ann);

        ArgumentCaptor<Progress> saved = ArgumentCaptor.forClass(Progress.class);
        verify(progressRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(ann.id());
        assertThat(saved.getValue().getCurrentChapter()).isEqualTo(5);
        assertThat(response.currentChapter()).isEqualTo(5);
        assertThat(response.totalChapters()).isEqualTo(20);
        assertThat(response.userName()).isEqualTo("Ann");
    }

    @Test
    void laterUpdatesChangeTheExistingRow() {
        Book dune = TestData.book("Dune", 20);
        Club club = clubReading(dune);
        Progress existing = new Progress();
        existing.setUserId(ann.id());
        existing.setClub(club);
        existing.setBook(dune);
        existing.setCurrentChapter(2);
        when(progressRepository.findByUserIdAndClubIdAndBookId(ann.id(), club.getId(), dune.getId()))
                .thenReturn(Optional.of(existing));

        progressService.updateProgress(club.getId(), new UpdateProgressRequest(7), ann);

        verify(progressRepository).save(existing);
        assertThat(existing.getCurrentChapter()).isEqualTo(7);
    }

    @Test
    void myProgressIsNullUntilSomethingIsLogged() {
        Book dune = TestData.book("Dune", 20);
        Club club = clubReading(dune);
        when(progressRepository.findByUserIdAndClubIdAndBookId(ann.id(), club.getId(), dune.getId()))
                .thenReturn(Optional.empty());

        assertThat(progressService.getMyProgress(club.getId(), ann)).isNull();
    }

    @Test
    void clubProgressListsEveryMemberWithTheirName() {
        Book dune = TestData.book("Dune", 20);
        Club club = clubReading(dune);
        UUID bobId = UUID.randomUUID();
        Progress bobProgress = new Progress();
        bobProgress.setId(UUID.randomUUID());
        bobProgress.setUserId(bobId);
        bobProgress.setClub(club);
        bobProgress.setBook(dune);
        bobProgress.setCurrentChapter(12);
        when(progressRepository.findByClubIdAndBookId(club.getId(), dune.getId())).thenReturn(List.of(bobProgress));
        when(userDirectory.namesFor(anyCollection())).thenReturn(Map.of(bobId, "Bob"));

        List<ProgressResponse> all = progressService.getClubProgress(club.getId(), ann.id());

        assertThat(all).hasSize(1);
        assertThat(all.get(0).userName()).isEqualTo("Bob");
        assertThat(all.get(0).currentChapter()).isEqualTo(12);
        assertThat(all.get(0).totalChapters()).isEqualTo(20);
    }

    @Test
    void clubProgressIsEmptyWhenThereIsNoBook() {
        Club club = clubReading(null);

        assertThat(progressService.getClubProgress(club.getId(), ann.id())).isEmpty();
    }
}
