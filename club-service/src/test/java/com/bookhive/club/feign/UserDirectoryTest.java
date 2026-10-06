package com.bookhive.club.feign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;

import com.bookhive.club.TestData;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"rawtypes", "unchecked"})
class UserDirectoryTest {

    /** A circuit breaker that just runs the call and uses the fallback on any exception. */
    static class PassThroughCircuitBreaker implements CircuitBreaker {
        @Override
        public <T> T run(Supplier<T> toRun, Function<Throwable, T> fallback) {
            try {
                return toRun.get();
            } catch (Throwable t) {
                return fallback.apply(t);
            }
        }
    }

    @Mock
    private UserClient userClient;

    @Mock
    private CircuitBreakerFactory circuitBreakerFactory;

    private UserDirectory directory;

    @BeforeEach
    void setUp() {
        directory = new UserDirectory(userClient, circuitBreakerFactory);
    }

    @AfterEach
    void tearDown() {
        TestData.clearAuthentication();
    }

    private void circuitBreakerIsAvailable() {
        when(circuitBreakerFactory.create("user-service")).thenReturn(new PassThroughCircuitBreaker());
    }

    @Test
    void resolvesNamesWithOneBatchedCall() {
        circuitBreakerIsAvailable();
        UUID ann = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        when(userClient.getUsers(isNull(), anyList()))
                .thenReturn(List.of(new UserSummary(ann, "Ann"), new UserSummary(bob, "Bob")));

        Map<UUID, String> names = directory.namesFor(List.of(ann, bob, ann));

        assertThat(names).containsEntry(ann, "Ann").containsEntry(bob, "Bob");
        ArgumentCaptor<List<UUID>> sent = ArgumentCaptor.forClass(List.class);
        verify(userClient).getUsers(isNull(), sent.capture());
        assertThat(sent.getValue()).containsExactly(ann, bob);   // de-duplicated, order kept
    }

    @Test
    void forwardsTheCallersJwtToUserService() {
        circuitBreakerIsAvailable();
        TestData.authenticate(TestData.member("Ann"));
        UUID id = UUID.randomUUID();
        when(userClient.getUsers(eq("Bearer jwt-token"), anyList())).thenReturn(List.of(new UserSummary(id, "Ann")));

        assertThat(directory.namesFor(List.of(id))).containsEntry(id, "Ann");
    }

    @Test
    void whenUserServiceIsDownTheFallbackGivesPlaceholderNames() {
        circuitBreakerIsAvailable();
        UUID id = UUID.randomUUID();
        when(userClient.getUsers(isNull(), anyList())).thenThrow(new IllegalStateException("connection refused"));

        assertThat(directory.namesFor(List.of(id))).isEmpty();
        assertThat(directory.nameFor(id)).isEqualTo(UserDirectory.UNKNOWN_USER);
    }

    @Test
    void emptyOrNullIdsNeverCallUserService() {
        assertThat(directory.namesFor(List.of())).isEmpty();
        assertThat(directory.namesFor(java.util.Arrays.asList((UUID) null))).isEmpty();
        assertThat(directory.nameFor(null)).isEqualTo(UserDirectory.UNKNOWN_USER);

        verifyNoInteractions(userClient);
        verifyNoInteractions(circuitBreakerFactory);
    }
}
