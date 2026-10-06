package com.bookhive.club.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.bookhive.club.TestData;
import com.bookhive.club.dto.BookResponse;
import com.bookhive.club.dto.CreateBookRequest;
import com.bookhive.club.dto.PageResponse;
import com.bookhive.club.exception.ForbiddenException;
import com.bookhive.club.exception.GlobalExceptionHandler;
import com.bookhive.club.exception.NotFoundException;
import com.bookhive.club.security.AuthenticatedUser;
import com.bookhive.club.service.BookService;
import com.bookhive.club.service.ExternalBookService;

@ExtendWith(MockitoExtension.class)
class BookControllerTest {

    @Mock
    private BookService bookService;

    @Mock
    private ExternalBookService externalBookService;

    private MockMvc mockMvc;
    private final AuthenticatedUser member = TestData.member("Mia");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BookController(bookService, externalBookService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        TestData.authenticate(member);
    }

    @AfterEach
    void tearDown() {
        TestData.clearAuthentication();
    }

    private static BookResponse book(String title) {
        return new BookResponse(UUID.randomUUID(), title, "Author", 10, null, null, null, null);
    }

    @Test
    void paginatedCatalogReturnsTheServicePage() throws Exception {
        when(bookService.getAllBooksPaged(0, 12))
                .thenReturn(new PageResponse<>(List.of(book("Dune")), 0, 12, 1, 1));

        mockMvc.perform(get("/api/books/paginated"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"title\":\"Dune\"")))
                .andExpect(content().string(containsString("\"totalPages\":1")));
    }

    @Test
    void customPageAndSizeArePassedThrough() throws Exception {
        when(bookService.getAllBooksPaged(2, 5)).thenReturn(new PageResponse<>(List.of(), 2, 5, 11, 3));

        mockMvc.perform(get("/api/books/paginated").param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"page\":2")));
    }

    @Test
    void membersGetAForbiddenResponseWhenAddingABook() throws Exception {
        when(bookService.createBook(any(CreateBookRequest.class), any(AuthenticatedUser.class)))
                .thenThrow(new ForbiddenException("Only admins can add books"));

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"X\",\"author\":\"Y\",\"totalChapters\":3}"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("Only admins can add books")));
    }

    @Test
    void unknownBookIsA404WithAMessage() throws Exception {
        UUID id = UUID.randomUUID();
        when(bookService.getBookById(id)).thenThrow(new NotFoundException("Book not found"));

        mockMvc.perform(get("/api/books/" + id))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Book not found")));
    }

    @Test
    void deletingABookInUseIsABadRequest() throws Exception {
        UUID id = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Can't delete a book that's currently being read by a club"))
                .when(bookService).deleteBook(any(UUID.class), any(AuthenticatedUser.class));

        mockMvc.perform(delete("/api/books/" + id))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("currently being read")));
    }

    @Test
    void searchWithoutTermsIsABadRequest() throws Exception {
        when(externalBookService.searchBooks(null, null, null))
                .thenThrow(new IllegalArgumentException("Provide a keyword, author, or category to search"));

        mockMvc.perform(get("/api/books/search"))
                .andExpect(status().isBadRequest());
    }
}
