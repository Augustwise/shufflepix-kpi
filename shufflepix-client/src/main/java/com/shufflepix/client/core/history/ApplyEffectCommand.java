package com.shufflepix.client.core.history;

import com.shufflepix.client.core.effect.EffectRegistry;
import com.shufflepix.client.core.model.Layer;
import com.shufflepix.client.core.model.Project;

import java.util.Map;

public class ApplyEffectCommand implements Command {
    private final Project project;
    private final Layer layer;
    private final EffectRegistry registry;
    private final String code;
    private final Map<String, Object> parameters;

    public ApplyEffectCommand(Project project, Layer layer, EffectRegistry registry,
                              String code, Map<String, Object> parameters) {
        this.project = project;
        this.layer = layer;
        this.registry = registry;
        this.code = code;
        this.parameters = parameters;
    }

    @Override
    public String getName() {
        return "Застосувати ефект";
    }

    @Override
    public void execute() {
        layer.applyEffect(registry, code, parameters);
        project.markDirty();
    }

    @Override
    public void undo() {
        layer.removeEffect(layer.getEffects().size() - 1);
        project.markDirty();
    }
}
