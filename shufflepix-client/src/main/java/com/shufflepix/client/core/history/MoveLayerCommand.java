package com.shufflepix.client.core.history;

import com.shufflepix.client.core.model.Project;

public class MoveLayerCommand implements Command {
    private final Project project;
    private final int from;
    private final int to;

    public MoveLayerCommand(Project project, int from, int to) {
        this.project = project;
        this.from = from;
        this.to = to;
    }

    @Override
    public String getName() {
        return "Перемістити шар";
    }

    @Override
    public void execute() {
        project.moveLayer(from, to);
    }

    @Override
    public void undo() {
        project.moveLayer(to, from);
    }
}
