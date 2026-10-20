package com.shufflepix.server.repository;

import com.shufflepix.server.domain.ProjectThumbnailEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProjectThumbnailRepository extends JpaRepository<ProjectThumbnailEntity, UUID> {
}
