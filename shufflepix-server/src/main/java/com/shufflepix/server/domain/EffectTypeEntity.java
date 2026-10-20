package com.shufflepix.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Entity
@Table(name = "effect_types")
public class EffectTypeEntity {
    @Id
    @Column(length = 20)
    private String code;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_parameters", nullable = false)
    private Map<String, Object> defaultParameters;

    protected EffectTypeEntity() {
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> getDefaultParameters() {
        return defaultParameters;
    }
}
