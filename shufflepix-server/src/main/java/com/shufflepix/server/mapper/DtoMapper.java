package com.shufflepix.server.mapper;

import com.shufflepix.common.dto.EffectDto;
import com.shufflepix.common.dto.EffectTypeDto;
import com.shufflepix.common.dto.LayerDto;
import com.shufflepix.common.dto.ProjectDto;
import com.shufflepix.common.dto.ProjectSummaryDto;
import com.shufflepix.server.domain.EffectTypeEntity;
import com.shufflepix.server.domain.LayerEffectEntity;
import com.shufflepix.server.domain.LayerEntity;
import com.shufflepix.server.domain.ProjectEntity;

public final class DtoMapper {
    private DtoMapper() {
    }

    public static ProjectSummaryDto toSummary(ProjectEntity p) {
        return new ProjectSummaryDto(p.getId(), p.getName(), p.getWidth(), p.getHeight(), p.getUpdatedAt());
    }

    public static ProjectDto toDto(ProjectEntity p) {
        return new ProjectDto(p.getId(), p.getName(), p.getWidth(), p.getHeight(),
                p.getBackgroundColor(), p.getVersion(), p.getCreatedAt(), p.getUpdatedAt());
    }

    public static LayerDto toDto(LayerEntity l) {
        return new LayerDto(l.getId(), l.getName(), l.getPosition(), l.isVisible(), l.getOpacity(),
                l.getEffects().stream().map(DtoMapper::toDto).toList());
    }

    public static EffectDto toDto(LayerEffectEntity e) {
        return new EffectDto(e.getEffectType().getCode(), e.getParameters());
    }

    public static EffectTypeDto toDto(EffectTypeEntity t) {
        return new EffectTypeDto(t.getCode(), t.getName(), t.getDefaultParameters());
    }
}
