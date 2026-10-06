package com.bookhive.club.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bookhive.club.entity.Book;

public interface BookRepository extends JpaRepository<Book, UUID> {
}
