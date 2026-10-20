package com.shufflepix.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_thumbnails")
public class ProjectThumbnailEntity {
    @Id
    @Column(name = "project_id")
    private UUID projectId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private ProjectEntity project;

    @Column(name = "image_png", nullable = false)
    private byte[] imagePng;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProjectThumbnailEntity() {
    }

    public ProjectThumbnailEntity(ProjectEntity project) {
        this.project = project;
    }

    @PrePersist
    @PreUpdate
    void onSave() {
        updatedAt = Instant.now();
    }

    public UUID getProjectId() {
        return projectId;
    }

    public byte[] getImagePng() {
        return imagePng;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(byte[] imagePng, int width, int height) {
        this.imagePng = imagePng;
        this.width = width;
        this.height = height;
    }
}
