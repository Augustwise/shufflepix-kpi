package com.shufflepix.server.api;

import com.shufflepix.common.api.ApiPaths;
import com.shufflepix.common.dto.ProjectDto;
import com.shufflepix.common.dto.ProjectSummaryDto;
import com.shufflepix.common.dto.RenameProjectRequest;
import com.shufflepix.common.dto.SaveProjectRequest;
import com.shufflepix.server.service.ProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class ProjectController {
    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping(ApiPaths.PROJECTS)
    public List<ProjectSummaryDto> list() {
        return projectService.listProjects();
    }

    @GetMapping(ApiPaths.PROJECT)
    public ProjectDto get(@PathVariable UUID projectId) {
        return projectService.getProject(projectId);
    }

    @PostMapping(path = ApiPaths.PROJECTS, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectDto create(@RequestPart(ApiPaths.PART_PROJECT) SaveProjectRequest project,
                             MultipartHttpServletRequest request) {
        return projectService.createProject(project, readContents(request));
    }

    @PutMapping(path = ApiPaths.PROJECT, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProjectDto save(@PathVariable UUID projectId,
                           @RequestPart(ApiPaths.PART_PROJECT) SaveProjectRequest project,
                           MultipartHttpServletRequest request) {
        return projectService.saveProject(projectId, project, readContents(request));
    }

    @PatchMapping(ApiPaths.PROJECT)
    public ProjectDto rename(@PathVariable UUID projectId, @RequestBody RenameProjectRequest body) {
        return projectService.renameProject(projectId, body.name());
    }

    @DeleteMapping(ApiPaths.PROJECT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID projectId) {
        projectService.deleteProject(projectId);
    }

    private static Map<String, byte[]> readContents(MultipartHttpServletRequest request) {
        Map<String, byte[]> contents = new HashMap<>();
        for (Map.Entry<String, MultipartFile> part : request.getFileMap().entrySet()) {
            if (ApiPaths.PART_PROJECT.equals(part.getKey())) {
                continue;
            }
            try {
                contents.put(part.getKey(), part.getValue().getBytes());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return contents;
    }
}
