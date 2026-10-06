package com.bookhive.club.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bookhive.club.entity.Comment;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    Page<Comment> findByClubIdAndChapterNumber(UUID clubId, Integer chapterNumber, Pageable pageable);

    /** Everyone who has already commented on this chapter - they are the ones to notify about a new reply. */
    @Query("select distinct c.userId from Comment c where c.club.id = :clubId and c.chapterNumber = :chapter")
    List<UUID> findParticipantIds(@Param("clubId") UUID clubId, @Param("chapter") Integer chapter);

    void deleteByClubId(UUID clubId);
}
