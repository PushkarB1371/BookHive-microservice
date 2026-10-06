package com.bookhive.club.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bookhive.club.TestData;
import com.bookhive.club.cache.BookCache;
import com.bookhive.club.dto.BookResponse;
import com.bookhive.club.dto.CreateBookRequest;
import com.bookhive.club.dto.PageResponse;
import com.bookhive.club.entity.Book;
import com.bookhive.club.exception.ForbiddenException;
import com.bookhive.club.exception.NotFoundException;
import com.bookhive.club.repository.BookRepository;
import com.bookhive.club.repository.ClubRepository;
import com.bookhive.club.repository.ProgressRepository;
import com.bookhive.club.repository.ReviewRepository;
import com.bookhive.club.security.AuthenticatedUser;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock private BookRepository bookRepository;
    @Mock private ClubRepository clubRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private ProgressRepository progressRepository;
    @Mock private BookCache bookCache;

    @InjectMocks
    private BookService bookService;

    private final AuthenticatedUser admin = TestData.admin();
    private final AuthenticatedUser member = TestData.member("Mia");

    @Test
    void adminCanAddABookAndTheCatalogCacheIsCleared() {
        CreateBookRequest request = new CreateBookRequest(
                "  Deep Work ", "Cal Newport", 9, "", "About focus", "Self-Help", "2016");

        BookResponse response = bookService.createBook(request, admin);

        ArgumentCaptor<Book> saved = ArgumentCaptor.forClass(Book.class);
        verify(bookRepository).save(saved.capture());
        assertThat(saved.getValue().getTitle()).isEqualTo("Deep Work");
        assertThat(saved.getValue().getAddedById()).isEqualTo(admin.id());
        assertThat(saved.getValue().getCoverImageUrl()).isNull();          // blank becomes null
        assertThat(response.title()).isEqualTo("Deep Work");
        verify(bookCache).evictAll();
    }

    @Test
    void aMemberCannotAddABook() {
        CreateBookRequest request = new CreateBookRequest("Any", "Author", 5, null, null, null, null);

        assertThatThrownBy(() -> bookService.createBook(request, member))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Only admins");

        verify(bookRepository, never()).save(any());
        verifyNoInteractions(bookCache);
    }

    @Test
    void aBookNeedsATitleAnAuthorAndAtLeastOneChapter() {
        assertThatThrownBy(() -> bookService.createBook(
                new CreateBookRequest(" ", "Author", 5, null, null, null, null), admin))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bookService.createBook(
                new CreateBookRequest("Title", null, 5, null, null, null, null), admin))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bookService.createBook(
                new CreateBookRequest("Title", "Author", 0, null, null, null, null), admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chapter");
        assertThatThrownBy(() -> bookService.createBook(
                new CreateBookRequest("Title", "Author", null, null, null, null, null), admin))
                .isInstanceOf(IllegalArgumentException.class);

        verify(bookRepository, never()).save(any());
    }

    @Test
    void catalogIsServedFromTheCacheWhenPresent() {
        BookResponse cached = new BookResponse(UUID.randomUUID(), "Dune", "Herbert", 20, null, null, null, null);
        when(bookCache.getAll()).thenReturn(Optional.of(List.of(cached)));

        assertThat(bookService.getAllBooks()).containsExactly(cached);

        verifyNoInteractions(bookRepository);
    }

    @Test
    void catalogIsLoadedFromTheDatabaseAndCachedOnAMiss() {
        when(bookCache.getAll()).thenReturn(Optional.empty());
        when(bookRepository.findAll()).thenReturn(List.of(TestData.book("Dune", 20)));

        List<BookResponse> books = bookService.getAllBooks();

        assertThat(books).extracting(BookResponse::title).containsExactly("Dune");
        verify(bookCache).putAll(books);
    }

    @Test
    void catalogPagesAreSlicedFromTheFullList() {
        when(bookCache.getAll()).thenReturn(Optional.empty());
        when(bookRepository.findAll()).thenReturn(List.of(
                TestData.book("A", 1), TestData.book("B", 1), TestData.book("C", 1)));

        PageResponse<BookResponse> page = bookService.getAllBooksPaged(0, 2);

        assertThat(page.content()).extracting(BookResponse::title).containsExactly("A", "B");
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(2);
    }

    @Test
    void singleBookIsCachedAfterTheFirstLookup() {
        Book book = TestData.book("Dune", 20);
        when(bookCache.get(book.getId())).thenReturn(Optional.empty());
        when(bookRepository.findById(book.getId())).thenReturn(Optional.of(book));

        BookResponse response = bookService.getBookById(book.getId());

        assertThat(response.title()).isEqualTo("Dune");
        verify(bookCache).put(response);
    }

    @Test
    void unknownBookIsNotFound() {
        UUID missing = UUID.randomUUID();
        when(bookCache.get(missing)).thenReturn(Optional.empty());
        when(bookRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.getBookById(missing))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Book not found");
    }

    @Test
    void aBookBeingReadByAClubCannotBeDeleted() {
        Book book = TestData.book("Dune", 20);
        when(bookRepository.findById(book.getId())).thenReturn(Optional.of(book));
        when(clubRepository.existsByCurrentBookId(book.getId())).thenReturn(true);

        assertThatThrownBy(() -> bookService.deleteBook(book.getId(), admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currently being read");

        verify(bookRepository, never()).delete(any());
    }

    @Test
    void deletingABookAlsoRemovesItsReviewsAndProgressAndClearsTheCache() {
        Book book = TestData.book("Dune", 20);
        when(bookRepository.findById(book.getId())).thenReturn(Optional.of(book));
        when(clubRepository.existsByCurrentBookId(book.getId())).thenReturn(false);

        bookService.deleteBook(book.getId(), admin);

        verify(reviewRepository).deleteByBookId(book.getId());
        verify(progressRepository).deleteByBookId(book.getId());
        verify(bookRepository).delete(book);
        verify(bookCache).evictAll();
        verify(bookCache).evict(book.getId());
    }

    @Test
    void aMemberCannotDeleteABook() {
        assertThatThrownBy(() -> bookService.deleteBook(UUID.randomUUID(), member))
                .isInstanceOf(ForbiddenException.class);

        verify(bookRepository, never()).delete(any());
    }
}
