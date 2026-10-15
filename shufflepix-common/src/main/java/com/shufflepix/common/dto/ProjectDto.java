package com.shufflepix.common.dto;

import java.time.Instant;
import java.util.UUID;

public record ProjectDto(
        UUID id,
        String name,
        int width,
        int height,
        String backgroundColor,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
