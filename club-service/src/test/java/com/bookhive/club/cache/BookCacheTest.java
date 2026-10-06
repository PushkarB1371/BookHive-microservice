package com.bookhive.club.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.bookhive.club.dto.BookResponse;

@ExtendWith(MockitoExtension.class)
class BookCacheTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> values;

    private BookCache cache;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(values);
        cache = new BookCache(redis, 10);
    }

    private static BookResponse book(UUID id) {
        return new BookResponse(id, "Dune", "Frank Herbert", 20, null, "Spice", "Fiction", "1965");
    }

    @Test
    void cachedCatalogIsParsedBackIntoBooks() {
        UUID id = UUID.randomUUID();
        when(values.get("books:all")).thenReturn(
                "[{\"id\":\"" + id + "\",\"title\":\"Dune\",\"author\":\"Frank Herbert\",\"totalChapters\":20,"
                        + "\"coverImageUrl\":null,\"description\":\"Spice\",\"category\":\"Fiction\",\"publishedDate\":\"1965\"}]");

        Optional<List<BookResponse>> result = cache.getAll();

        assertThat(result).isPresent();
        assertThat(result.get()).containsExactly(book(id));
    }

    @Test
    void missIsEmpty() {
        when(values.get("books:all")).thenReturn(null);

        assertThat(cache.getAll()).isEmpty();
    }

    @Test
    void redisFailureFallsBackToTheDatabaseInsteadOfBreakingTheRequest() {
        when(values.get(anyString())).thenThrow(new IllegalStateException("redis is down"));

        assertThat(cache.getAll()).isEmpty();
        assertThat(cache.get(UUID.randomUUID())).isEmpty();
    }

    @Test
    void garbageInRedisIsTreatedAsAMiss() {
        when(values.get("books:all")).thenReturn("this is not json");

        assertThat(cache.getAll()).isEmpty();
    }

    @Test
    void writesJsonWithTheConfiguredTtl() {
        UUID id = UUID.randomUUID();

        cache.putAll(List.of(book(id)));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(values).set(eq("books:all"), json.capture(), eq(Duration.ofMinutes(10)));
        assertThat(json.getValue()).contains("\"title\":\"Dune\"").contains(id.toString());
    }

    @Test
    void singleBookRoundTripsThroughItsOwnKey() {
        UUID id = UUID.randomUUID();
        cache.put(book(id));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(values).set(eq("books:" + id), json.capture(), any(Duration.class));

        when(values.get("books:" + id)).thenReturn(json.getValue());
        assertThat(cache.get(id)).contains(book(id));
    }

    @Test
    void writeFailureIsSwallowed() {
        doThrow(new IllegalStateException("redis is down")).when(values).set(anyString(), anyString(), any(Duration.class));

        assertThatCode(() -> cache.putAll(List.of(book(UUID.randomUUID())))).doesNotThrowAnyException();
    }

    @Test
    void evictionDeletesTheRightKeysAndSwallowsFailures() {
        UUID id = UUID.randomUUID();

        cache.evictAll();
        cache.evict(id);

        verify(redis).delete("books:all");
        verify(redis).delete("books:" + id);

        doThrow(new IllegalStateException("redis is down")).when(redis).delete(anyString());
        assertThatCode(() -> cache.evictAll()).doesNotThrowAnyException();
    }
}
