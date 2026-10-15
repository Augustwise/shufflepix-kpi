package com.shufflepix.common.dto;

import java.util.List;
import java.util.UUID;

public record LayerDto(
        UUID id,
        String name,
        int position,
        boolean visible,
        double opacity,
        List<EffectDto> effects) {
}
