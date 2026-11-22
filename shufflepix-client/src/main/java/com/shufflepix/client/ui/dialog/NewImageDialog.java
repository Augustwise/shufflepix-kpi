package com.shufflepix.client.ui.dialog;

import com.shufflepix.client.core.style.Color;
import com.shufflepix.common.api.Limits;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

public class NewImageDialog extends Dialog<NewImageDialog.Result> {
    public record Result(int width, int height, Color background) {
    }

    private static final String WHITE = "Білий";
    private static final String TRANSPARENT = "Прозорий";
    private static final String CUSTOM = "Довільний";

    public NewImageDialog(Window owner) {
        initOwner(owner);
        setTitle("Нове зображення");

        Spinner<Integer> width = new Spinner<>(1, Limits.MAX_CANVAS_SIZE, 800);
        Spinner<Integer> height = new Spinner<>(1, Limits.MAX_CANVAS_SIZE, 600);
        width.setEditable(true);
        height.setEditable(true);
        ComboBox<String> background = new ComboBox<>();
        background.getItems().addAll(WHITE, TRANSPARENT, CUSTOM);
        background.setValue(WHITE);
        ColorPicker custom = new ColorPicker(javafx.scene.paint.Color.WHITE);
        custom.disableProperty().bind(background.valueProperty().isNotEqualTo(CUSTOM));

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.addRow(0, new Label("Ширина, пікселів:"), width);
        grid.addRow(1, new Label("Висота, пікселів:"), height);
        grid.addRow(2, new Label("Колір фону:"), background, custom);
        getDialogPane().setContent(grid);

        ButtonType create = new ButtonType("Створити", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Скасувати", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(create, cancel);

        setResultConverter(button -> {
            if (button != create) {
                return null;
            }
            Color bg = switch (background.getValue()) {
                case TRANSPARENT -> Color.TRANSPARENT;
                case CUSTOM -> Color.fromFx(custom.getValue());
                default -> Color.WHITE;
            };
            return new Result(width.getValue(), height.getValue(), bg);
        });
    }
}
