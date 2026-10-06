package com.bookhive.club.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bookhive.club.entity.Club;

public interface ClubRepository extends JpaRepository<Club, UUID> {

    List<Club> findByCurrentBookId(UUID bookId);

    boolean existsByCurrentBookId(UUID bookId);
}
