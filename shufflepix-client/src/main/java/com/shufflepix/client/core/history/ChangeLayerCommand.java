package com.shufflepix.client.core.history;

import com.shufflepix.client.core.model.Layer;
import com.shufflepix.client.core.model.Project;

public class ChangeLayerCommand implements Command {
    private final Project project;
    private final Layer layer;
    private final String name;
    private final boolean visible;
    private final double opacity;
    private String oldName;
    private boolean oldVisible;
    private double oldOpacity;

    public ChangeLayerCommand(Project project, Layer layer, String name, boolean visible, double opacity) {
        this.project = project;
        this.layer = layer;
        this.name = name;
        this.visible = visible;
        this.opacity = opacity;
    }

    @Override
    public String getName() {
        return "Змінити параметри шару";
    }

    @Override
    public void execute() {
        oldName = layer.getName();
        oldVisible = layer.isVisible();
        oldOpacity = layer.getOpacity();
        apply(name, visible, opacity);
    }

    @Override
    public void undo() {
        apply(oldName, oldVisible, oldOpacity);
    }

    private void apply(String n, boolean v, double o) {
        layer.setName(n);
        layer.setVisible(v);
        layer.setOpacity(o);
        project.markDirty();
    }
}
