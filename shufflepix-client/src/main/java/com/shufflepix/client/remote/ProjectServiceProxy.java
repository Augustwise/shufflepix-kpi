package com.shufflepix.client.remote;

import com.fasterxml.jackson.core.type.TypeReference;
import com.shufflepix.common.api.ApiPaths;
import com.shufflepix.common.dto.ProjectDto;
import com.shufflepix.common.dto.ProjectSummaryDto;
import com.shufflepix.common.dto.RenameProjectRequest;
import com.shufflepix.common.dto.SaveProjectRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ProjectServiceProxy implements ProjectService {
    private final RestClient rest;

    public ProjectServiceProxy(RestClient rest) {
        this.rest = rest;
    }

    @Override
    public List<ProjectSummaryDto> listProjects() {
        return rest.getJson(ApiPaths.PROJECTS, new TypeReference<>() { });
    }

    @Override
    public ProjectDto getProject(UUID projectId) {
        return rest.getJson(RestClient.path(ApiPaths.PROJECT, projectId), new TypeReference<>() { });
    }

    @Override
    public ProjectDto createProject(SaveProjectRequest request, Map<String, byte[]> pngParts) {
        return rest.sendMultipart("POST", ApiPaths.PROJECTS, request, pngParts, ProjectDto.class);
    }

    @Override
    public ProjectDto saveProject(UUID projectId, SaveProjectRequest request, Map<String, byte[]> pngParts) {
        return rest.sendMultipart("PUT", RestClient.path(ApiPaths.PROJECT, projectId),
                request, pngParts, ProjectDto.class);
    }

    @Override
    public ProjectDto renameProject(UUID projectId, String newName) {
        return rest.sendJson("PATCH", RestClient.path(ApiPaths.PROJECT, projectId),
                new RenameProjectRequest(newName), ProjectDto.class);
    }

    @Override
    public void deleteProject(UUID projectId) {
        rest.delete(RestClient.path(ApiPaths.PROJECT, projectId));
    }
}
