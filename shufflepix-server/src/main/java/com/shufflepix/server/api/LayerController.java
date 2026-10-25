package com.shufflepix.server.api;

import com.shufflepix.common.api.ApiPaths;
import com.shufflepix.common.dto.EffectTypeDto;
import com.shufflepix.common.dto.LayerDto;
import com.shufflepix.server.service.LayerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class LayerController {
    private final LayerService layerService;

    public LayerController(LayerService layerService) {
        this.layerService = layerService;
    }

    @GetMapping(ApiPaths.PROJECT_LAYERS)
    public List<LayerDto> layers(@PathVariable UUID projectId) {
        return layerService.getLayers(projectId);
    }

    @GetMapping(path = ApiPaths.LAYER_CONTENT, produces = ApiPaths.IMAGE_PNG)
    public byte[] content(@PathVariable UUID layerId) {
        return layerService.getLayerContent(layerId);
    }

    @GetMapping(ApiPaths.EFFECT_TYPES)
    public List<EffectTypeDto> effectTypes() {
        return layerService.getEffectTypes();
    }
}
