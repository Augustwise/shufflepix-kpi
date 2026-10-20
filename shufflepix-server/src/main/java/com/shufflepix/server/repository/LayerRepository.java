package com.shufflepix.server.repository;

import com.shufflepix.server.domain.LayerEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LayerRepository extends JpaRepository<LayerEntity, UUID> {
    @EntityGraph(attributePaths = {"effects", "effects.effectType"})
    List<LayerEntity> findByProjectIdOrderByPositionAsc(UUID projectId);
}
