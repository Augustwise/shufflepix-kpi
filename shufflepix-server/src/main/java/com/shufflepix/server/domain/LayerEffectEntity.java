package com.shufflepix.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "layer_effects")
public class LayerEffectEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "layer_id", nullable = false)
    private LayerEntity layer;

    @Column(nullable = false)
    private int position;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "effect_code", nullable = false)
    private EffectTypeEntity effectType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> parameters = new HashMap<>();

    protected LayerEffectEntity() {
    }

    public LayerEffectEntity(LayerEntity layer, int position,
                             EffectTypeEntity effectType, Map<String, Object> parameters) {
        this.layer = layer;
        this.position = position;
        this.effectType = effectType;
        this.parameters = parameters;
    }

    public UUID getId() {
        return id;
    }

    public LayerEntity getLayer() {
        return layer;
    }

    public int getPosition() {
        return position;
    }

    public EffectTypeEntity getEffectType() {
        return effectType;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }
}
