package com.bookhive.club.cache;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.bookhive.club.dto.BookResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Caches the book catalog in Redis as plain JSON strings. Every operation is fail-open:
 * if Redis is down or a cached value is unreadable we just fall back to the database.
 */
@Component
public class BookCache {

    static final String ALL_KEY = "books:all";
    static final String BOOK_KEY_PREFIX = "books:";

    private static final Logger log = LoggerFactory.getLogger(BookCache.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Duration ttl;

    public BookCache(StringRedisTemplate redis, @Value("${bookhive.cache.ttl-minutes:10}") long ttlMinutes) {
        this.redis = redis;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public Optional<List<BookResponse>> getAll() {
        return read(ALL_KEY, new TypeReference<List<BookResponse>>() { });
    }

    public void putAll(List<BookResponse> books) {
        write(ALL_KEY, books);
    }

    public Optional<BookResponse> get(UUID id) {
        return read(BOOK_KEY_PREFIX + id, new TypeReference<BookResponse>() { });
    }

    public void put(BookResponse book) {
        write(BOOK_KEY_PREFIX + book.id(), book);
    }

    public void evictAll() {
        delete(ALL_KEY);
    }

    public void evict(UUID id) {
        delete(BOOK_KEY_PREFIX + id);
    }

    private <T> Optional<T> read(String key, TypeReference<T> type) {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(mapper.readValue(json, type));
        } catch (Exception e) {
            log.warn("Cache read failed for {} ({}), using the database", key, e.toString());
            return Optional.empty();
        }
    }

    private void write(String key, Object value) {
        try {
            redis.opsForValue().set(key, mapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            log.warn("Cache write failed for {} ({})", key, e.toString());
        }
    }

    private void delete(String key) {
        try {
            redis.delete(key);
        } catch (Exception e) {
            log.warn("Cache evict failed for {} ({})", key, e.toString());
        }
    }
}
