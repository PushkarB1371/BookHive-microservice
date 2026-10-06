package com.bookhive.club.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bookhive.club.entity.Progress;

public interface ProgressRepository extends JpaRepository<Progress, UUID> {

    Optional<Progress> findByUserIdAndClubIdAndBookId(UUID userId, UUID clubId, UUID bookId);

    List<Progress> findByClubIdAndBookId(UUID clubId, UUID bookId);

    void deleteByClubId(UUID clubId);

    void deleteByBookId(UUID bookId);
}
