package com.shufflepix.client.core;

import com.shufflepix.client.core.history.Command;
import com.shufflepix.client.core.history.CommandHistory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandHistoryTest {
    private final List<Integer> state = new ArrayList<>();

    private Command add(int value) {
        return new Command() {
            @Override
            public String getName() {
                return "Додати " + value;
            }

            @Override
            public void execute() {
                state.add(value);
            }

            @Override
            public void undo() {
                state.remove(state.size() - 1);
            }
        };
    }

    @Test
    void undoAndRedo() {
        CommandHistory history = new CommandHistory();
        history.execute(add(1));
        history.execute(add(2));
        history.undo();
        assertEquals(List.of(1), state);
        history.redo();
        assertEquals(List.of(1, 2), state);
    }

    @Test
    void newCommandClearsRedo() {
        CommandHistory history = new CommandHistory();
        history.execute(add(1));
        history.undo();
        history.execute(add(2));
        assertFalse(history.canRedo());
    }

    @Test
    void keepsOnlyLastTwentyOperations() {
        CommandHistory history = new CommandHistory();
        for (int i = 0; i < 25; i++) {
            history.execute(add(i));
        }
        int undone = 0;
        while (history.canUndo()) {
            history.undo();
            undone++;
        }
        assertEquals(CommandHistory.DEFAULT_LIMIT, undone);
        assertTrue(history.canRedo());
        assertEquals(5, state.size());
    }
}
