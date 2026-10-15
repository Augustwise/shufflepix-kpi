package com.shufflepix.common.dto;

import java.util.Map;

public record EffectTypeDto(
        String code,
        String name,
        Map<String, Object> defaultParameters) {
}
