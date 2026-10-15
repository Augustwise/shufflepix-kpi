package com.shufflepix.common.dto;

import java.time.Instant;
import java.util.UUID;

public record ProjectSummaryDto(
        UUID id,
        String name,
        int width,
        int height,
        Instant updatedAt) {
}
