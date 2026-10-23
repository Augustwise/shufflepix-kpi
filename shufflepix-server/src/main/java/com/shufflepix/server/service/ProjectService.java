package com.shufflepix.server.service;

import com.shufflepix.common.dto.ProjectDto;
import com.shufflepix.common.dto.ProjectSummaryDto;
import com.shufflepix.common.dto.SaveLayerRequest;
import com.shufflepix.common.dto.SaveProjectRequest;
import com.shufflepix.server.domain.ProjectEntity;
import com.shufflepix.server.mapper.DtoMapper;
import com.shufflepix.server.repository.ProjectRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProjectService {
    private final ProjectRepository projects;
    private final LayerService layerService;
    private final SaveProjectValidator validator;
    private final ApplicationEventPublisher events;

    public ProjectService(ProjectRepository projects, LayerService layerService,
                          SaveProjectValidator validator, ApplicationEventPublisher events) {
        this.projects = projects;
        this.layerService = layerService;
        this.validator = validator;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<ProjectSummaryDto> listProjects() {
        return projects.findAllByOrderByUpdatedAtDesc().stream()
                .map(DtoMapper::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectDto getProject(UUID projectId) {
        return DtoMapper.toDto(find(projectId));
    }

    @Transactional
    public ProjectDto createProject(SaveProjectRequest request, Map<String, byte[]> contents) {
        Map<String, byte[]> safeContents = contents == null ? Map.of() : contents;
        validator.validate(request, safeContents);
        for (SaveLayerRequest layer : request.layers()) {
            if (layer.id() != null) {
                throw new InvalidRequestException("Новий проєкт не може посилатися на наявні шари");
            }
        }
        String name = request.name().strip();
        if (projects.existsByNameIgnoreCase(name)) {
            throw new NameConflictException(name);
        }

        ProjectEntity project = new ProjectEntity();
        project.setName(name);
        project.setWidth(request.width());
        project.setHeight(request.height());
        project.setBackgroundColor(request.backgroundColor());
        project = projects.saveAndFlush(project);

        layerService.saveLayers(project, request.layers(), safeContents);
        events.publishEvent(new ProjectSavedEvent(project.getId()));
        return DtoMapper.toDto(project);
    }

    @Transactional
    public ProjectDto saveProject(UUID projectId, SaveProjectRequest request, Map<String, byte[]> contents) {
        Map<String, byte[]> safeContents = contents == null ? Map.of() : contents;
        validator.validate(request, safeContents);
        if (request.version() == null) {
            throw new InvalidRequestException("Не вказано версію проєкту");
        }

        ProjectEntity project = find(projectId);
        if (!request.version().equals(project.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(ProjectEntity.class, projectId);
        }

        String name = request.name().strip();
        if (projects.existsByNameIgnoreCaseAndIdNot(name, projectId)) {
            throw new NameConflictException(name);
        }

        boolean sizeChanged = project.getWidth() != request.width() || project.getHeight() != request.height();
        if (sizeChanged) {
            for (SaveLayerRequest layer : request.layers()) {
                if (layer.contentPart() == null) {
                    throw new InvalidRequestException(
                            "У разі зміни розмірів полотна потрібно передати вміст шару «" + layer.name() + "»");
                }
            }
        }

        project.setName(name);
        project.setWidth(request.width());
        project.setHeight(request.height());
        project.setBackgroundColor(request.backgroundColor());
        project.touch();
        project = projects.saveAndFlush(project);

        layerService.saveLayers(project, request.layers(), safeContents);
        events.publishEvent(new ProjectSavedEvent(project.getId()));
        return DtoMapper.toDto(project);
    }

    @Transactional
    public ProjectDto renameProject(UUID projectId, String newName) {
        validator.validateName(newName);
        String name = newName.strip();
        ProjectEntity project = find(projectId);
        if (projects.existsByNameIgnoreCaseAndIdNot(name, projectId)) {
            throw new NameConflictException(name);
        }
        project.setName(name);
        return DtoMapper.toDto(projects.saveAndFlush(project));
    }

    @Transactional
    public void deleteProject(UUID projectId) {
        if (!projects.existsById(projectId)) {
            throw new NotFoundException("Проєкт не знайдено");
        }
        projects.deleteById(projectId);
    }

    private ProjectEntity find(UUID projectId) {
        return projects.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Проєкт не знайдено"));
    }
}
