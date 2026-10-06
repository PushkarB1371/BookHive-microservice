package com.bookhive.club.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bookhive.club.dto.BookResponse;
import com.bookhive.club.dto.BookSearchResult;
import com.bookhive.club.dto.CreateBookRequest;
import com.bookhive.club.dto.PageResponse;
import com.bookhive.club.security.CurrentUser;
import com.bookhive.club.service.BookService;
import com.bookhive.club.service.ExternalBookService;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService bookService;
    private final ExternalBookService externalBookService;

    public BookController(BookService bookService, ExternalBookService externalBookService) {
        this.bookService = bookService;
        this.externalBookService = externalBookService;
    }

    @PostMapping
    public BookResponse createBook(@RequestBody CreateBookRequest request) {
        return bookService.createBook(request, CurrentUser.get());
    }

    @GetMapping
    public List<BookResponse> getAllBooks() {
        return bookService.getAllBooks();
    }

    @GetMapping("/paginated")
    public PageResponse<BookResponse> getBooksPaginated(@RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "12") int size) {
        return bookService.getAllBooksPaged(page, size);
    }

    @GetMapping("/search")
    public List<BookSearchResult> search(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) String author,
                                         @RequestParam(required = false) String category) {
        return externalBookService.searchBooks(q, author, category);
    }

    @GetMapping("/{id}")
    public BookResponse getBook(@PathVariable UUID id) {
        return bookService.getBookById(id);
    }

    @DeleteMapping("/{id}")
    public Map<String, String> deleteBook(@PathVariable UUID id) {
        bookService.deleteBook(id, CurrentUser.get());
        return Map.of("message", "Book deleted");
    }
}
