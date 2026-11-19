package com.shufflepix.client.remote;

import com.shufflepix.common.dto.ProjectDto;
import com.shufflepix.common.dto.ProjectSummaryDto;
import com.shufflepix.common.dto.SaveProjectRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ProjectService {
    List<ProjectSummaryDto> listProjects();

    ProjectDto getProject(UUID projectId);

    ProjectDto createProject(SaveProjectRequest request, Map<String, byte[]> pngParts);

    ProjectDto saveProject(UUID projectId, SaveProjectRequest request, Map<String, byte[]> pngParts);

    ProjectDto renameProject(UUID projectId, String newName);

    void deleteProject(UUID projectId);
}
