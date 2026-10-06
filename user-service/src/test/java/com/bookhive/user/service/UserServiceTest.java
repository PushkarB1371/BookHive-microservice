package com.bookhive.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bookhive.user.dto.ProfileResponse;
import com.bookhive.user.dto.UserSummary;
import com.bookhive.user.entity.User;
import com.bookhive.user.enums.UserRole;
import com.bookhive.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Captor
    private ArgumentCaptor<Iterable<UUID>> idsCaptor;

    @Test
    void summariesExposeOnlyIdAndName() {
        User ann = user("Ann");
        when(userRepository.findAllById(org.mockito.ArgumentMatchers.<Iterable<UUID>>any())).thenReturn(List.of(ann));

        List<UserSummary> result = userService.summaries(List.of(ann.getId()));

        assertThat(result).containsExactly(new UserSummary(ann.getId(), "Ann"));
    }

    @Test
    void emptyOrNullRequestNeverTouchesTheDatabase() {
        assertThat(userService.summaries(List.of())).isEmpty();
        assertThat(userService.summaries(null)).isEmpty();

        verifyNoInteractions(userRepository);
    }

    @Test
    void batchIsCappedAndDeduplicated() {
        UUID repeated = UUID.randomUUID();
        List<UUID> ids = new ArrayList<>();
        ids.add(repeated);
        ids.add(repeated);
        for (int i = 0; i < 300; i++) {
            ids.add(UUID.randomUUID());
        }
        when(userRepository.findAllById(org.mockito.ArgumentMatchers.<Iterable<UUID>>any())).thenReturn(List.of());

        userService.summaries(ids);

        org.mockito.Mockito.verify(userRepository).findAllById(idsCaptor.capture());
        long sent = StreamSupport.stream(idsCaptor.getValue().spliterator(), false).count();
        assertThat(sent).isEqualTo(UserService.MAX_BATCH);
    }

    @Test
    void profileReturnsEmailAndRole() {
        User ann = user("Ann");
        when(userRepository.findById(ann.getId())).thenReturn(Optional.of(ann));

        ProfileResponse profile = userService.profile(ann.getId());

        assertThat(profile.email()).isEqualTo("ann@example.com");
        assertThat(profile.role()).isEqualTo("MEMBER");
    }

    @Test
    void profileOfUnknownUserIsAnError() {
        UUID missing = UUID.randomUUID();
        when(userRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.profile(missing))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    private User user(String name) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName(name);
        user.setEmail("ann@example.com");
        user.setPassword("hash");
        user.setRole(UserRole.MEMBER);
        return user;
    }
}
