package com.bookhive.club.mapper;

import com.bookhive.club.dto.BookResponse;
import com.bookhive.club.entity.Book;

public final class BookMapper {

    private BookMapper() {
    }

    public static BookResponse toResponse(Book book) {
        return new BookResponse(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getTotalChapters(),
                book.getCoverImageUrl(),
                book.getDescription(),
                book.getCategory(),
                book.getPublishedDate());
    }
}
