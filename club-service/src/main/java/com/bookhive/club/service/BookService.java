package com.bookhive.club.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookhive.club.cache.BookCache;
import com.bookhive.club.dto.BookResponse;
import com.bookhive.club.dto.CreateBookRequest;
import com.bookhive.club.dto.PageResponse;
import com.bookhive.club.entity.Book;
import com.bookhive.club.exception.ForbiddenException;
import com.bookhive.club.exception.NotFoundException;
import com.bookhive.club.mapper.BookMapper;
import com.bookhive.club.repository.BookRepository;
import com.bookhive.club.repository.ClubRepository;
import com.bookhive.club.repository.ProgressRepository;
import com.bookhive.club.repository.ReviewRepository;
import com.bookhive.club.security.AuthenticatedUser;
import com.bookhive.club.util.PaginationUtil;

@Service
public class BookService {

    private final BookRepository bookRepository;
    private final ClubRepository clubRepository;
    private final ReviewRepository reviewRepository;
    private final ProgressRepository progressRepository;
    private final BookCache bookCache;

    public BookService(BookRepository bookRepository,
                       ClubRepository clubRepository,
                       ReviewRepository reviewRepository,
                       ProgressRepository progressRepository,
                       BookCache bookCache) {
        this.bookRepository = bookRepository;
        this.clubRepository = clubRepository;
        this.reviewRepository = reviewRepository;
        this.progressRepository = progressRepository;
        this.bookCache = bookCache;
    }

    @Transactional
    public BookResponse createBook(CreateBookRequest request, AuthenticatedUser user) {
        if (!user.isAdmin()) {
            throw new ForbiddenException("Only admins can add books");
        }
        if (request == null || isBlank(request.title()) || isBlank(request.author())) {
            throw new IllegalArgumentException("Title and author are required");
        }
        if (request.totalChapters() == null || request.totalChapters() < 1) {
            throw new IllegalArgumentException("Total chapters must be at least 1");
        }

        Book book = new Book();
        book.setTitle(request.title().trim());
        book.setAuthor(request.author().trim());
        book.setTotalChapters(request.totalChapters());
        book.setCoverImageUrl(blankToNull(request.coverImageUrl()));
        book.setDescription(blankToNull(request.description()));
        book.setCategory(blankToNull(request.category()));
        book.setPublishedDate(blankToNull(request.publishedDate()));
        book.setAddedById(user.id());
        bookRepository.save(book);

        bookCache.evictAll();
        return BookMapper.toResponse(book);
    }

    /** The whole catalog, served from Redis when possible. */
    public List<BookResponse> getAllBooks() {
        Optional<List<BookResponse>> cached = bookCache.getAll();
        if (cached.isPresent()) {
            return cached.get();
        }
        List<BookResponse> books = bookRepository.findAll().stream().map(BookMapper::toResponse).toList();
        bookCache.putAll(books);
        return books;
    }

    public PageResponse<BookResponse> getAllBooksPaged(int page, int size) {
        return PaginationUtil.paginate(getAllBooks(), page, size);
    }

    public BookResponse getBookById(UUID id) {
        Optional<BookResponse> cached = bookCache.get(id);
        if (cached.isPresent()) {
            return cached.get();
        }
        BookResponse book = BookMapper.toResponse(getBookEntity(id));
        bookCache.put(book);
        return book;
    }

    public Book getBookEntity(UUID id) {
        return bookRepository.findById(id).orElseThrow(() -> new NotFoundException("Book not found"));
    }

    @Transactional
    public void deleteBook(UUID bookId, AuthenticatedUser user) {
        if (!user.isAdmin()) {
            throw new ForbiddenException("Only admins can delete books");
        }
        Book book = getBookEntity(bookId);

        if (clubRepository.existsByCurrentBookId(bookId)) {
            throw new IllegalArgumentException("Can't delete a book that's currently being read by a club");
        }

        reviewRepository.deleteByBookId(bookId);
        progressRepository.deleteByBookId(bookId);
        bookRepository.delete(book);

        bookCache.evictAll();
        bookCache.evict(bookId);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
