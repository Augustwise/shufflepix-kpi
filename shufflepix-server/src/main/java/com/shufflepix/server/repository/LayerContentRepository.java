package com.shufflepix.server.repository;

import com.shufflepix.server.domain.LayerContentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LayerContentRepository extends JpaRepository<LayerContentEntity, UUID> {
}
