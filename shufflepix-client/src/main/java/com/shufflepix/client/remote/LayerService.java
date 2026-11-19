package com.shufflepix.client.remote;

import com.shufflepix.common.dto.EffectTypeDto;
import com.shufflepix.common.dto.LayerDto;

import java.util.List;
import java.util.UUID;

public interface LayerService {
    List<LayerDto> getLayers(UUID projectId);

    byte[] getLayerContent(UUID layerId);

    List<EffectTypeDto> getEffectTypes();
}
