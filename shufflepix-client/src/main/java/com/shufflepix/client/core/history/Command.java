package com.shufflepix.client.core.history;

public interface Command {
    String getName();

    void execute();

    void undo();

    default void redo() {
        execute();
    }
}
