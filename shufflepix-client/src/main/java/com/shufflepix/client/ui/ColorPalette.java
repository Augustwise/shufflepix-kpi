package com.shufflepix.client.ui;

import com.shufflepix.client.core.style.Color;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import java.util.List;
import java.util.function.Consumer;

public class ColorPalette extends HBox {
    private static final List<String> STANDARD_COLORS = List.of(
            "#000000", "#7F7F7F", "#FFFFFF", "#C3C3C3", "#880015", "#ED1C24", "#FF7F27", "#FFF200",
            "#22B14C", "#00A2E8", "#3F48CC", "#A349A4", "#B97A57", "#FFAEC9", "#B5E61D", "#99D9EA");

    private final RadioButton strokeTarget = new RadioButton("Контур");
    private final RadioButton fillTarget = new RadioButton("Заливка");
    private final ColorPicker strokePicker = new ColorPicker(javafx.scene.paint.Color.BLACK);
    private final ColorPicker fillPicker = new ColorPicker(javafx.scene.paint.Color.WHITE);

    public ColorPalette(Consumer<Color> onStrokeChanged, Consumer<Color> onFillChanged) {
        setSpacing(6);
        ToggleGroup target = new ToggleGroup();
        strokeTarget.setToggleGroup(target);
        fillTarget.setToggleGroup(target);
        strokeTarget.setSelected(true);

        strokePicker.setOnAction(e -> onStrokeChanged.accept(Color.fromFx(strokePicker.getValue())));
        fillPicker.setOnAction(e -> onFillChanged.accept(Color.fromFx(fillPicker.getValue())));

        getChildren().addAll(strokeTarget, strokePicker, fillTarget, fillPicker, new Label("  "));
        for (String hex : STANDARD_COLORS) {
            Region swatch = new Region();
            swatch.getStyleClass().add("palette-swatch");
            swatch.setStyle("-fx-background-color: " + hex + ";");
            javafx.scene.paint.Color fx = javafx.scene.paint.Color.web(hex);
            swatch.setOnMouseClicked(e -> {
                if (strokeTarget.isSelected()) {
                    strokePicker.setValue(fx);
                    onStrokeChanged.accept(Color.fromFx(fx));
                } else {
                    fillPicker.setValue(fx);
                    onFillChanged.accept(Color.fromFx(fx));
                }
            });
            getChildren().add(swatch);
        }
    }
}
