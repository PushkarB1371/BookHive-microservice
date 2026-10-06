package com.bookhive.club.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.bookhive.club.dto.BookSearchResult;
import com.bookhive.club.exception.ExternalServiceException;

class ExternalBookServiceTest {

    private final ExternalBookService service = new ExternalBookService("");

    @Test
    void parsesTitleAuthorCoverCategoryAndDescription() {
        String json = """
                {"items":[{"volumeInfo":{
                  "title":"Atomic Habits",
                  "authors":["James Clear","Someone Else"],
                  "description":"Tiny changes",
                  "publishedDate":"2018-10-16",
                  "categories":["Self-Help"],
                  "imageLinks":{"thumbnail":"http://books.google.com/cover.jpg"}}}]}
                """;

        List<BookSearchResult> results = service.parse(json);

        assertThat(results).containsExactly(new BookSearchResult(
                "Atomic Habits", "James Clear", "https://books.google.com/cover.jpg",
                "Tiny changes", "2018-10-16", "Self-Help"));
    }

    @Test
    void missingAuthorsBecomeUnknownAndEntriesWithoutATitleAreSkipped() {
        String json = """
                {"items":[
                  {"volumeInfo":{"title":"Anonymous Tales"}},
                  {"volumeInfo":{"authors":["Nobody"]}}
                ]}
                """;

        List<BookSearchResult> results = service.parse(json);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).author()).isEqualTo("Unknown author");
        assertThat(results.get(0).coverImageUrl()).isNull();
    }

    @Test
    void noItemsMeansNoResults() {
        assertThat(service.parse("{\"totalItems\":0}")).isEmpty();
    }

    @Test
    void unreadableResponseBecomesABadGatewayError() {
        assertThatThrownBy(() -> service.parse("<html>not json</html>"))
                .isInstanceOf(ExternalServiceException.class);
    }

    @Test
    void searchNeedsAtLeastOneTerm() {
        assertThatThrownBy(() -> service.searchBooks(" ", null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keyword");
    }
}
