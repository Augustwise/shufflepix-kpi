package com.shufflepix.client.ui.dialog;

import com.shufflepix.client.remote.ProjectStorage;
import com.shufflepix.common.dto.ProjectSummaryDto;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.stage.Window;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ProjectCatalogDialog extends Dialog<ProjectSummaryDto> {
    public enum Mode { OPEN, MANAGE }

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final ProjectStorage storage;
    private final ListView<ProjectSummaryDto> list = new ListView<>();

    public ProjectCatalogDialog(Window owner, ProjectStorage storage, Mode mode) {
        this.storage = storage;
        initOwner(owner);
        setTitle(mode == Mode.OPEN ? "Відкрити проєкт із сервера" : "Керування проєктами");
        setResizable(true);

        list.setPrefSize(480, 360);
        list.setPlaceholder(new javafx.scene.control.Label("Збережених проєктів немає"));
        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(ProjectSummaryDto p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? null
                        : p.name() + "   " + p.width() + "×" + p.height() + "   " + DATE.format(p.updatedAt()));
            }
        });
        getDialogPane().setContent(list);

        ButtonType close = new ButtonType("Закрити", ButtonBar.ButtonData.CANCEL_CLOSE);
        if (mode == Mode.OPEN) {
            ButtonType open = new ButtonType("Відкрити", ButtonBar.ButtonData.OK_DONE);
            getDialogPane().getButtonTypes().addAll(open, close);
            setResultConverter(b -> b == open ? list.getSelectionModel().getSelectedItem() : null);
        } else {
            getDialogPane().getButtonTypes().add(close);
            setResultConverter(b -> null);
        }
    }

    public void reload() {
        List<ProjectSummaryDto> projects = storage.catalog();
        list.getItems().setAll(projects);
    }
}
