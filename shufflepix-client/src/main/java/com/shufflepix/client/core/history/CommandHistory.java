package com.shufflepix.client.core.history;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

public class CommandHistory {
    public static final int DEFAULT_LIMIT = 20;

    private final int limit;
    private final Deque<Command> undoStack = new ArrayDeque<>();
    private final Deque<Command> redoStack = new ArrayDeque<>();
    private Runnable onChange = () -> { };

    public CommandHistory() {
        this(DEFAULT_LIMIT);
    }

    public CommandHistory(int limit) {
        this.limit = limit;
    }

    public void execute(Command command) {
        command.execute();
        undoStack.push(command);
        if (undoStack.size() > limit) {
            undoStack.removeLast();
        }
        redoStack.clear();
        onChange.run();
    }

    public void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        Command command = undoStack.pop();
        command.undo();
        redoStack.push(command);
        onChange.run();
    }

    public void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        Command command = redoStack.pop();
        command.redo();
        undoStack.push(command);
        onChange.run();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public Optional<String> nextUndoName() {
        return Optional.ofNullable(undoStack.peek()).map(Command::getName);
    }

    public Optional<String> nextRedoName() {
        return Optional.ofNullable(redoStack.peek()).map(Command::getName);
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
        onChange.run();
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }
}
