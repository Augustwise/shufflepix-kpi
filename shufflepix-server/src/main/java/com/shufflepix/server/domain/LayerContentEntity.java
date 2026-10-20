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
@Table(name = "layer_contents")
public class LayerContentEntity {
    @Id
    @Column(name = "layer_id")
    private UUID layerId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "layer_id")
    private LayerEntity layer;

    @Column(name = "image_png", nullable = false)
    private byte[] imagePng;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LayerContentEntity() {
    }

    public LayerContentEntity(LayerEntity layer, byte[] imagePng) {
        this.layer = layer;
        this.imagePng = imagePng;
    }

    @PrePersist
    @PreUpdate
    void onSave() {
        updatedAt = Instant.now();
    }

    public UUID getLayerId() {
        return layerId;
    }

    public LayerEntity getLayer() {
        return layer;
    }

    public byte[] getImagePng() {
        return imagePng;
    }

    public void setImagePng(byte[] imagePng) {
        this.imagePng = imagePng;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
