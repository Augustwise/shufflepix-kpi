package com.shufflepix.client.core.history;

import com.shufflepix.client.core.model.Layer;
import com.shufflepix.client.core.model.Project;

public class AddLayerCommand implements Command {
    private final Project project;
    private final Layer layer;
    private final int index;
    private final String name;

    public AddLayerCommand(Project project, Layer layer, int index, String name) {
        this.project = project;
        this.layer = layer;
        this.index = index;
        this.name = name;
    }

    public static AddLayerCommand newLayer(Project project, String layerName) {
        Layer layer = Layer.transparent(layerName, project.getWidth(), project.getHeight());
        return new AddLayerCommand(project, layer, project.getActiveIndex() + 1, "Додати шар");
    }

    public static AddLayerCommand duplicateActive(Project project) {
        Layer copy = project.getActiveLayer().copy();
        return new AddLayerCommand(project, copy, project.getActiveIndex() + 1, "Дублювати шар");
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void execute() {
        project.addLayer(index, layer);
    }

    @Override
    public void undo() {
        project.removeLayer(project.indexOf(layer));
    }
}
