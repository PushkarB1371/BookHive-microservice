package com.bookhive.club.service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.bookhive.club.dto.BookSearchResult;
import com.bookhive.club.exception.ExternalServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Searches the public Google Books catalog (used to pre-fill the "add a book" form and the Discover page). */
@Service
public class ExternalBookService {

    private static final Logger log = LoggerFactory.getLogger(ExternalBookService.class);
    private static final int MAX_RESULTS = 20;

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey;

    public ExternalBookService(@Value("${google.books.api.key:}") String apiKey) {
        this.apiKey = apiKey;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public List<BookSearchResult> searchBooks(String query, String author, String category) {
        List<String> parts = new ArrayList<>();
        if (query != null && !query.isBlank()) {
            parts.add(query.trim());
        }
        if (author != null && !author.isBlank()) {
            parts.add("inauthor:" + author.trim());
        }
        if (category != null && !category.isBlank()) {
            parts.add("subject:" + category.trim());
        }
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("Provide a keyword, author, or category to search");
        }

        String url = "https://www.googleapis.com/books/v1/volumes?q="
                + URLEncoder.encode(String.join(" ", parts), StandardCharsets.UTF_8)
                + "&orderBy=relevance&maxResults=" + MAX_RESULTS;
        if (apiKey != null && !apiKey.isBlank()) {
            url += "&key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        }

        String response;
        try {
            response = restClient.get().uri(URI.create(url)).retrieve().body(String.class);
        } catch (Exception e) {
            log.warn("Google Books request failed: {}", e.toString());
            throw new ExternalServiceException("Could not reach Google Books right now. Try again in a moment.");
        }
        return parse(response);
    }

    List<BookSearchResult> parse(String json) {
        List<BookSearchResult> results = new ArrayList<>();
        try {
            for (JsonNode item : objectMapper.readTree(json).path("items")) {
                JsonNode info = item.path("volumeInfo");

                String title = info.path("title").asText(null);
                if (title == null) {
                    continue;
                }

                JsonNode authors = info.path("authors");
                String author = authors.isArray() && authors.size() > 0 ? authors.get(0).asText() : "Unknown author";

                String cover = info.path("imageLinks").path("thumbnail").asText(null);
                if (cover != null) {
                    cover = cover.replace("http://", "https://");
                }

                JsonNode categories = info.path("categories");
                String category = categories.isArray() && categories.size() > 0 ? categories.get(0).asText() : null;

                results.add(new BookSearchResult(
                        title,
                        author,
                        cover,
                        info.path("description").asText(null),
                        info.path("publishedDate").asText(null),
                        category));
            }
        } catch (Exception e) {
            throw new ExternalServiceException("Google Books returned something we couldn't read");
        }
        return results;
    }
}
