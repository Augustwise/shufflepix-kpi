package com.shufflepix.server.repository;

import com.shufflepix.server.domain.EffectTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EffectTypeRepository extends JpaRepository<EffectTypeEntity, String> {
}
