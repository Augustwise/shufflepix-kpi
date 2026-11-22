package com.shufflepix.client.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

import java.util.Optional;

public final class Dialogs {
    private Dialogs() {
    }

    public static void error(Window owner, String message) {
        show(owner, Alert.AlertType.ERROR, "Помилка", message);
    }

    public static void info(Window owner, String message) {
        show(owner, Alert.AlertType.INFORMATION, "ShufflePix", message);
    }

    public static void warning(Window owner, String message) {
        show(owner, Alert.AlertType.WARNING, "Увага", message);
    }

    public static boolean confirm(Window owner, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.initOwner(owner);
        alert.setTitle("Підтвердження");
        alert.setHeaderText(null);
        Optional<ButtonType> answer = alert.showAndWait();
        return answer.isPresent() && answer.get() == ButtonType.YES;
    }

    private static void show(Window owner, Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
