package com.shufflepix.common.dto;

import java.util.Map;

public record EffectDto(
        String effectCode,
        Map<String, Object> parameters) {
}
