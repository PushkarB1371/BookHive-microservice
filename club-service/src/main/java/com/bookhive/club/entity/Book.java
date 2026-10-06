package com.bookhive.club.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String author;

    @Column(name = "total_chapters", nullable = false)
    private Integer totalChapters;

    @Column(name = "cover_image_url", length = 1000)
    private String coverImageUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String category;

    @Column(name = "published_date")
    private String publishedDate;

    /** Id of the admin (in user-service) who added the book. A plain id, not a foreign key. */
    @Column(name = "added_by_id")
    private UUID addedById;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public Integer getTotalChapters() { return totalChapters; }
    public void setTotalChapters(Integer totalChapters) { this.totalChapters = totalChapters; }
    public String getCoverImageUrl() { return coverImageUrl; }
    public void setCoverImageUrl(String coverImageUrl) { this.coverImageUrl = coverImageUrl; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getPublishedDate() { return publishedDate; }
    public void setPublishedDate(String publishedDate) { this.publishedDate = publishedDate; }
    public UUID getAddedById() { return addedById; }
    public void setAddedById(UUID addedById) { this.addedById = addedById; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
