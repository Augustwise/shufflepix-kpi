package com.shufflepix.client.remote;

import com.shufflepix.client.core.effect.EffectRegistry;
import com.shufflepix.client.core.model.Layer;
import com.shufflepix.client.core.model.Project;
import com.shufflepix.client.core.style.Color;
import com.shufflepix.client.io.ImageFileService;
import com.shufflepix.common.api.Limits;
import com.shufflepix.common.dto.EffectDto;
import com.shufflepix.common.dto.LayerDto;
import com.shufflepix.common.dto.ProjectDto;
import com.shufflepix.common.dto.ProjectSummaryDto;
import com.shufflepix.common.dto.SaveLayerRequest;
import com.shufflepix.common.dto.SaveProjectRequest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class ProjectStorage {
    private final ProjectService projects;
    private final LayerService layers;
    private final ThumbnailService thumbnails;
    private final ImageFileService images;
    private final EffectRegistry effects;

    public ProjectStorage(ProjectService projects, LayerService layers, ThumbnailService thumbnails,
                          ImageFileService images, EffectRegistry effects) {
        this.projects = Objects.requireNonNull(projects, "projects");
        this.layers = Objects.requireNonNull(layers, "layers");
        this.thumbnails = Objects.requireNonNull(thumbnails, "thumbnails");
        this.images = Objects.requireNonNull(images, "images");
        this.effects = Objects.requireNonNull(effects, "effects");
    }

    public ProjectDto save(Project project) {
        Objects.requireNonNull(project, "Проєкт не може бути null");
        List<Layer> projectLayers = project.getLayers();
        if (projectLayers.isEmpty()) {
            throw new IllegalArgumentException("Проєкт повинен містити щонайменше один шар");
        }

        boolean isNewProject = project.getServerId() == null;
        List<SaveLayerRequest> layerRequests = new ArrayList<>(projectLayers.size());
        Map<String, byte[]> pngParts = new LinkedHashMap<>();

        for (int pos = 0; pos < projectLayers.size(); pos++) {
            Layer layer = projectLayers.get(pos);
            UUID layerId = isNewProject ? null : layer.getServerId();
            boolean uploadContent = layerId == null || layer.isContentChanged();
            String contentPart = null;
            if (uploadContent) {
                contentPart = "layer-" + pos;
                pngParts.put(contentPart, images.encodePng(layer.getContent().getImage()));
            }
            List<EffectDto> effectDtos = layer.getEffects().stream()
                    .map(effect -> new EffectDto(effect.getCode(), effect.getParameters()))
                    .toList();
            layerRequests.add(new SaveLayerRequest(
                    layerId,
                    layer.getName(),
                    pos,
                    layer.isVisible(),
                    layer.getOpacity(),
                    effectDtos,
                    contentPart));
        }

        SaveProjectRequest request = new SaveProjectRequest(
                project.getName(),
                project.getWidth(),
                project.getHeight(),
                project.getBackgroundColor().toHex(),
                isNewProject ? null : project.getServerVersion(),
                layerRequests);

        ProjectDto saved = isNewProject
                ? projects.createProject(request, pngParts)
                : projects.saveProject(project.getServerId(), request, pngParts);
        if (saved == null || saved.id() == null) {
            throw new RemoteException(0, "BAD_RESPONSE", "Сервер повернув некоректні дані збереженого проєкту");
        }

        List<LayerDto> savedLayers = layers.getLayers(saved.id());
        Map<Integer, UUID> idByPosition = new HashMap<>();
        if (savedLayers != null) {
            for (LayerDto dto : savedLayers) {
                idByPosition.put(dto.position(), dto.id());
            }
        }
        for (int pos = 0; pos < projectLayers.size(); pos++) {
            Layer layer = projectLayers.get(pos);
            UUID assignedId = idByPosition.getOrDefault(pos, layer.getServerId());
            if (assignedId == null) {
                throw new RemoteException(0, "BAD_RESPONSE",
                        "Сервер не повернув ідентифікатор збереженого шару «" + layer.getName() + "»");
            }
            layer.markSaved(assignedId);
        }

        project.setName(saved.name());
        project.markSaved(saved.id(), saved.version());
        return saved;
    }

    public Project open(UUID projectId) {
        Objects.requireNonNull(projectId, "Ідентифікатор проєкту не може бути null");
        ProjectDto dto = projects.getProject(projectId);
        if (dto == null) {
            throw new RemoteException(0, "BAD_RESPONSE", "Сервер повернув порожні дані проєкту");
        }
        if (dto.width() < 1 || dto.width() > Limits.MAX_CANVAS_SIZE
                || dto.height() < 1 || dto.height() > Limits.MAX_CANVAS_SIZE) {
            throw new RemoteException(0, "BAD_RESPONSE",
                    "Некоректні розміри проєкту: " + dto.width() + "×" + dto.height());
        }
        Color background;
        try {
            background = Color.fromHex(dto.backgroundColor());
        } catch (IllegalArgumentException e) {
            throw new RemoteException(0, "BAD_RESPONSE",
                    "Некоректний колір фону проєкту: " + dto.backgroundColor());
        }

        List<LayerDto> layerDtos = layers.getLayers(projectId);
        if (layerDtos == null || layerDtos.isEmpty()) {
            throw new RemoteException(0, "BAD_RESPONSE", "Проєкт на сервері не містить жодного шару");
        }
        List<LayerDto> orderedLayers = layerDtos.stream()
                .sorted(Comparator.comparingInt(LayerDto::position))
                .toList();

        Project project = new Project(dto.width(), dto.height(), background);
        project.setName(dto.name());

        try {
            for (LayerDto layerDto : orderedLayers) {
                if (layerDto.id() == null) {
                    throw new RemoteException(0, "BAD_RESPONSE", "Шар на сервері не має ідентифікатора");
                }
                RemoteImageProxy imageProxy = new RemoteImageProxy(
                        layerDto.id(), dto.width(), dto.height(), layers, images);
                Layer layer = new Layer(layerDto.name(), imageProxy);
                layer.setVisible(layerDto.visible());
                layer.setOpacity(layerDto.opacity());
                if (layerDto.effects() != null) {
                    for (EffectDto effectDto : layerDto.effects()) {
                        layer.applyEffect(effects, effectDto.effectCode(), effectDto.parameters());
                    }
                }
                layer.markSaved(layerDto.id());
                project.addLayer(project.getLayers().size(), layer);
            }
        } catch (IllegalArgumentException e) {
            throw new RemoteException(0, "BAD_RESPONSE", "Некоректні параметри шару або ефекту: " + e.getMessage());
        }

        int activeIndex = project.getLayers().size() - 1;
        for (int i = project.getLayers().size() - 1; i >= 0; i--) {
            if (project.getLayers().get(i).isVisible()) {
                activeIndex = i;
                break;
            }
        }
        project.setActiveIndex(activeIndex);
        project.markSaved(dto.id(), dto.version());
        return project;
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
