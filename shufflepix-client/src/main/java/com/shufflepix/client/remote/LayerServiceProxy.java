package com.shufflepix.client.remote;

import com.fasterxml.jackson.core.type.TypeReference;
import com.shufflepix.common.api.ApiPaths;
import com.shufflepix.common.dto.EffectTypeDto;
import com.shufflepix.common.dto.LayerDto;

import java.util.List;
import java.util.UUID;

public class LayerServiceProxy implements LayerService {
    private final RestClient rest;

    public LayerServiceProxy(RestClient rest) {
        this.rest = rest;
    }

    @Override
    public List<LayerDto> getLayers(UUID projectId) {
        return rest.getJson(RestClient.path(ApiPaths.PROJECT_LAYERS, projectId), new TypeReference<>() { });
    }

    @Override
    public byte[] getLayerContent(UUID layerId) {
        return rest.getBytes(RestClient.path(ApiPaths.LAYER_CONTENT, layerId));
    }

    @Override
    public List<EffectTypeDto> getEffectTypes() {
        return rest.getJson(ApiPaths.EFFECT_TYPES, new TypeReference<>() { });
    }
}
