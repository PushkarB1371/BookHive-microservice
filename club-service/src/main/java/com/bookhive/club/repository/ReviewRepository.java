package com.bookhive.club.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bookhive.club.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Page<Review> findByBookId(UUID bookId, Pageable pageable);

    boolean existsByBookIdAndUserId(UUID bookId, UUID userId);

    @Query("select avg(r.rating) from Review r where r.book.id = :bookId")
    Double findAverageRating(@Param("bookId") UUID bookId);

    void deleteByBookId(UUID bookId);
}
