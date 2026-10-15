package com.shufflepix.common.dto;

import java.util.List;

public record SaveProjectRequest(
        String name,
        int width,
        int height,
        String backgroundColor,
        Long version,
        List<SaveLayerRequest> layers) {
}
