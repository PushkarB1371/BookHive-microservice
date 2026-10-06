package com.bookhive.club.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bookhive.club.entity.Membership;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    List<Membership> findByUserId(UUID userId);

    List<Membership> findByClubId(UUID clubId);

    Optional<Membership> findByUserIdAndClubId(UUID userId, UUID clubId);

    boolean existsByUserIdAndClubId(UUID userId, UUID clubId);

    void deleteByClubId(UUID clubId);
}
