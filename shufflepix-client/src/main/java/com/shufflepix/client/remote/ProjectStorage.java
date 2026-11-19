package com.shufflepix.client.remote;

import com.shufflepix.client.core.effect.EffectRegistry;
import com.shufflepix.client.core.model.Project;
import com.shufflepix.client.io.ImageFileService;
import com.shufflepix.common.dto.ProjectDto;
import com.shufflepix.common.dto.ProjectSummaryDto;

import java.util.List;
import java.util.UUID;

public class ProjectStorage {
    private final ProjectService projects;
    private final LayerService layers;
    private final ThumbnailService thumbnails;
    private final ImageFileService images;
    private final EffectRegistry effects;

    public ProjectStorage(ProjectService projects, LayerService layers, ThumbnailService thumbnails,
                          ImageFileService images, EffectRegistry effects) {
        this.projects = projects;
        this.layers = layers;
        this.thumbnails = thumbnails;
        this.images = images;
        this.effects = effects;
    }

    public ProjectDto save(Project project) {
        throw new UnsupportedOperationException("ProjectStorage.save");
    }

    public Project open(UUID projectId) {
        throw new UnsupportedOperationException("ProjectStorage.open");
    }

    public List<ProjectSummaryDto> catalog() {
        return projects.listProjects();
    }

    public byte[] thumbnail(UUID projectId) {
        return thumbnails.getThumbnail(projectId);
    }

    public ProjectDto rename(UUID projectId, String newName) {
        return projects.renameProject(projectId, newName);
    }

    public void delete(UUID projectId) {
        projects.deleteProject(projectId);
    }
}
