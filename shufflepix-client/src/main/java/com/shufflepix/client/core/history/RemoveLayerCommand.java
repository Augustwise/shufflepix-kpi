package com.shufflepix.client.core.history;

import com.shufflepix.client.core.model.Layer;
import com.shufflepix.client.core.model.Project;

public class RemoveLayerCommand implements Command {
    private final Project project;
    private final int index;
    private Layer removed;

    public RemoveLayerCommand(Project project, int index) {
        this.project = project;
        this.index = index;
    }

    @Override
    public String getName() {
        return "Видалити шар";
    }

    @Override
    public void execute() {
        removed = project.removeLayer(index);
    }

    @Override
    public void undo() {
        project.addLayer(index, removed);
    }
}
