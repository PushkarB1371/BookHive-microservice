package com.bookhive.club.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.bookhive.club.enums.ClubRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "memberships", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "club_id"}))
public class Membership {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_in_club", nullable = false)
    private ClubRole roleInClub;

    @Column(name = "joined_at", updatable = false)
    private LocalDateTime joinedAt;

    @PrePersist
    protected void onCreate() {
        this.joinedAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public Club getClub() { return club; }
    public void setClub(Club club) { this.club = club; }
    public ClubRole getRoleInClub() { return roleInClub; }
    public void setRoleInClub(ClubRole roleInClub) { this.roleInClub = roleInClub; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
}
