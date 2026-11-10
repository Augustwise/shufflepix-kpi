package com.shufflepix.client.core.history;

import com.shufflepix.client.core.effect.Effect;
import com.shufflepix.client.core.model.Layer;
import com.shufflepix.client.core.model.Project;

public class RemoveEffectCommand implements Command {
    private final Project project;
    private final Layer layer;
    private final int index;
    private Effect removed;

    public RemoveEffectCommand(Project project, Layer layer, int index) {
        this.project = project;
        this.layer = layer;
        this.index = index;
    }

    @Override
    public String getName() {
        return "Вилучити ефект";
    }

    @Override
    public void execute() {
        removed = layer.removeEffect(index);
        project.markDirty();
    }

    @Override
    public void undo() {
        layer.insertEffect(index, removed);
        project.markDirty();
    }
}
