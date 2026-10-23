package com.shufflepix.server.service;

import com.shufflepix.common.dto.EffectDto;
import com.shufflepix.common.dto.EffectTypeDto;
import com.shufflepix.common.dto.LayerDto;
import com.shufflepix.common.dto.SaveLayerRequest;
import com.shufflepix.server.domain.EffectTypeEntity;
import com.shufflepix.server.domain.LayerContentEntity;
import com.shufflepix.server.domain.LayerEffectEntity;
import com.shufflepix.server.domain.LayerEntity;
import com.shufflepix.server.domain.ProjectEntity;
import com.shufflepix.server.mapper.DtoMapper;
import com.shufflepix.server.repository.EffectTypeRepository;
import com.shufflepix.server.repository.LayerContentRepository;
import com.shufflepix.server.repository.LayerRepository;
import com.shufflepix.server.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class LayerService {
    private final ProjectRepository projects;
    private final LayerRepository layers;
    private final LayerContentRepository contents;
    private final EffectTypeRepository effectTypes;

    public LayerService(ProjectRepository projects, LayerRepository layers,
                        LayerContentRepository contents, EffectTypeRepository effectTypes) {
        this.projects = projects;
        this.layers = layers;
        this.contents = contents;
        this.effectTypes = effectTypes;
    }

    @Transactional(readOnly = true)
    public List<LayerDto> getLayers(UUID projectId) {
        if (!projects.existsById(projectId)) {
            throw new NotFoundException("Проєкт не знайдено");
        }
        return layers.findByProjectIdOrderByPositionAsc(projectId).stream()
                .map(DtoMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public byte[] getLayerContent(UUID layerId) {
        return contents.findById(layerId)
                .orElseThrow(() -> new NotFoundException("Вміст шару не знайдено"))
                .getImagePng();
    }

    @Transactional(readOnly = true)
    public List<EffectTypeDto> getEffectTypes() {
        return effectTypes.findAll().stream()
                .map(DtoMapper::toDto)
                .toList();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void saveLayers(ProjectEntity project, List<SaveLayerRequest> requested, Map<String, byte[]> contentsMap) {
        if (requested == null || requested.isEmpty()) {
            throw new InvalidRequestException("Проєкт повинен містити щонайменше один шар");
        }
        Map<String, byte[]> safeContents = contentsMap == null ? Map.of() : contentsMap;
        List<LayerEntity> existingLayers = layers.findByProjectIdOrderByPositionAsc(project.getId());
        Map<UUID, LayerEntity> existingById = new LinkedHashMap<>();
        for (LayerEntity existing : existingLayers) {
            existingById.put(existing.getId(), existing);
        }

        Set<UUID> keepIds = new HashSet<>();
        Set<String> requestedEffectCodes = new HashSet<>();
        for (SaveLayerRequest req : requested) {
            if (req.id() != null) {
                if (!existingById.containsKey(req.id())) {
                    throw new InvalidRequestException("Шар не належить цьому проєкту або не існує: " + req.id());
                }
                keepIds.add(req.id());
            }
            if (req.effects() != null) {
                for (EffectDto effect : req.effects()) {
                    requestedEffectCodes.add(effect.effectCode());
                }
            }
        }

        Map<String, EffectTypeEntity> effectTypeByCode = loadEffectTypes(requestedEffectCodes);

        // Remove layers deleted by the user (cascades layer_contents and layer_effects).
        List<LayerEntity> toDelete = new ArrayList<>();
        for (LayerEntity existing : existingLayers) {
            if (!keepIds.contains(existing.getId())) {
                toDelete.add(existing);
            }
        }
        if (!toDelete.isEmpty()) {
            layers.deleteAll(toDelete);
            layers.flush();
        }

        // Update existing layers (clearing old effects) and create new ones.
        // DEFERRABLE INITIALLY DEFERRED on (project_id, position) allows swapping positions safely.
        List<LayerEntity> savedEntities = new ArrayList<>(requested.size());
        for (SaveLayerRequest req : requested) {
            LayerEntity entity;
            if (req.id() != null) {
                entity = existingById.get(req.id());
                entity.setName(req.name().strip());
                entity.setPosition(req.position());
                entity.setVisible(req.visible());
                entity.setOpacity(req.opacity());
                entity.getEffects().clear();
            } else {
                entity = new LayerEntity();
                entity.setProject(project);
                entity.setName(req.name().strip());
                entity.setPosition(req.position());
                entity.setVisible(req.visible());
                entity.setOpacity(req.opacity());
                entity = layers.save(entity);
            }
            savedEntities.add(entity);
        }
        // Flush cleared effects and updated layer positions before inserting new effect chains.
        layers.flush();

        // Populate new effect chains and save/update layer contents only for new or changed layers.
        for (int i = 0; i < requested.size(); i++) {
            SaveLayerRequest req = requested.get(i);
            LayerEntity entity = savedEntities.get(i);

            List<EffectDto> reqEffects = req.effects() == null ? List.of() : req.effects();
            for (int pos = 0; pos < reqEffects.size(); pos++) {
                EffectDto dto = reqEffects.get(pos);
                EffectTypeEntity effectType = effectTypeByCode.get(dto.effectCode());
                Map<String, Object> params = dto.parameters() != null
                        ? new HashMap<>(dto.parameters())
                        : (effectType.getDefaultParameters() != null
                                ? new HashMap<>(effectType.getDefaultParameters())
                                : new HashMap<>());
                entity.getEffects().add(new LayerEffectEntity(entity, pos, effectType, params));
            }

            if (req.contentPart() != null) {
                byte[] png = safeContents.get(req.contentPart());
                if (png == null || png.length == 0) {
                    throw new InvalidRequestException("Відсутній вміст шару «" + req.name() + "»");
                }
                if (req.id() == null) {
                    contents.save(new LayerContentEntity(entity, png));
                } else {
                    LayerContentEntity existingContent = contents.findById(entity.getId()).orElse(null);
                    if (existingContent != null) {
                        existingContent.setImagePng(png);
                    } else {
                        contents.save(new LayerContentEntity(entity, png));
                    }
                }
            } else if (!contents.existsById(entity.getId())) {
                throw new InvalidRequestException("Для шару «" + req.name() + "» відсутній збережений вміст");
            }
        }

        layers.flush();
        contents.flush();
    }

    private Map<String, EffectTypeEntity> loadEffectTypes(Set<String> codes) {
        Map<String, EffectTypeEntity> result = new HashMap<>();
        if (codes.isEmpty()) {
            return result;
        }
        for (EffectTypeEntity type : effectTypes.findAllById(codes)) {
            result.put(type.getCode(), type);
        }
        for (String code : codes) {
            if (!result.containsKey(code)) {
                throw new InvalidRequestException("Невідомий тип ефекту: " + code);
            }
        }
        return result;
    }
}
