package com.shufflepix.client.ui;

import com.shufflepix.client.core.Editor;
import com.shufflepix.client.core.effect.BlurEffect;
import com.shufflepix.client.core.effect.Effect;
import com.shufflepix.client.core.history.AddLayerCommand;
import com.shufflepix.client.core.history.ApplyEffectCommand;
import com.shufflepix.client.core.history.ChangeLayerCommand;
import com.shufflepix.client.core.history.MoveLayerCommand;
import com.shufflepix.client.core.history.RemoveEffectCommand;
import com.shufflepix.client.core.history.RemoveLayerCommand;
import com.shufflepix.client.core.image.RasterImage;
import com.shufflepix.client.core.model.Layer;
import com.shufflepix.client.core.model.Project;
import com.shufflepix.client.core.render.LayerCompositor;
import com.shufflepix.client.core.shape.TextLabel;
import com.shufflepix.client.core.style.Color;
import com.shufflepix.client.core.tool.EllipseTool;
import com.shufflepix.client.core.tool.LineTool;
import com.shufflepix.client.core.tool.PencilTool;
import com.shufflepix.client.core.tool.RectangleTool;
import com.shufflepix.client.core.tool.TextTool;
import com.shufflepix.client.core.tool.Tool;
import com.shufflepix.client.io.ImageFile;
import com.shufflepix.client.io.ImageFileException;
import com.shufflepix.client.io.ImageFileService;
import com.shufflepix.client.io.ImageFormat;
import com.shufflepix.client.remote.ProjectStorage;
import com.shufflepix.client.remote.RemoteException;
import com.shufflepix.client.remote.ServerUnavailableException;
import com.shufflepix.client.ui.dialog.BlurDialog;
import com.shufflepix.client.ui.dialog.NewImageDialog;
import com.shufflepix.client.ui.dialog.ProjectCatalogDialog;
import com.shufflepix.common.dto.ProjectSummaryDto;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainController {
    private final Editor editor;
    private final ImageFileService files;
    private final ProjectStorage storage;
    private final LayerCompositor compositor = new LayerCompositor();
    private final Map<String, Tool> tools = Map.of(
            "pencil", PencilTool.pencil(),
            "line", new LineTool(),
            "rectangle", new RectangleTool(),
            "ellipse", new EllipseTool(),
            "eraser", PencilTool.eraser(),
            "text", new TextTool());

    @FXML private ToggleGroup toolGroup;
    @FXML private Spinner<Integer> lineWidthSpinner;
    @FXML private CheckBox fillCheck;
    @FXML private ComboBox<String> fontCombo;
    @FXML private Spinner<Integer> fontSizeSpinner;
    @FXML private ScrollPane canvasScroll;
    @FXML private ListView<Layer> layerList;
    @FXML private Button removeLayerButton;
    @FXML private Slider opacitySlider;
    @FXML private ListView<String> effectList;
    @FXML private HBox paletteBox;
    @FXML private Label statusLabel;
    @FXML private Label zoomLabel;
    @FXML private MenuItem undoItem;
    @FXML private MenuItem redoItem;

    private Stage stage;
    private CanvasView canvasView;
    private boolean refreshing;

    public MainController(Editor editor, ImageFileService files, ProjectStorage storage) {
        this.editor = editor;
        this.files = files;
        this.storage = storage;
    }

    @FXML
    private void initialize() {
        canvasView = new CanvasView(editor);
        canvasView.setErrorHandler(this::showError);
        canvasView.setOnHiddenLayer(() -> Dialogs.warning(stage,
                "Малювати на прихованому шарі неможливо. Зробіть шар видимим."));
        canvasScroll.setContent(canvasView);

        paletteBox.getChildren().add(new ColorPalette(editor::setStrokeColor, editor::setFillColor));

        toolGroup.selectedToggleProperty().addListener((obs, old, selected) -> onToolSelected(old, selected));
        lineWidthSpinner.setValueFactory(new javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100, 2));
        lineWidthSpinner.valueProperty().addListener((o, a, v) -> editor.setLineWidth(v));
        fillCheck.selectedProperty().addListener((o, a, v) -> editor.setFillEnabled(v));
        fontCombo.setItems(FXCollections.observableArrayList(Font.getFamilies()));
        fontCombo.setValue(Editor.DEFAULT_FONT);
        fontCombo.valueProperty().addListener((o, a, v) -> editor.setFontFamily(v));
        fontSizeSpinner.setValueFactory(new javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory(6, 200, 16));
        fontSizeSpinner.valueProperty().addListener((o, a, v) -> editor.setFontSize(v));

        layerList.setCellFactory(v -> new LayerCell());
        layerList.getSelectionModel().selectedIndexProperty().addListener((o, a, index) -> onLayerSelected(index.intValue()));
        opacitySlider.valueChangingProperty().addListener((o, wasChanging, changing) -> {
            if (!changing) {
                onOpacityChanged();
            }
        });

        editor.setTextInputHandler(this::onTextRequested);
        editor.addChangeListener(this::refresh);
        refresh();
    }

    public void setStage(Stage stage) {
        this.stage = stage;
        refreshTitle();
    }

    @FXML
    private void onNewImage() {
        if (!confirmDiscardChanges()) {
            return;
        }
        new NewImageDialog(stage).showAndWait().ifPresent(r -> {
            editor.setProject(Project.blank(r.width(), r.height(), r.background()));
            canvasView.setZoom(1.0);
            refresh();
        });
    }

    @FXML
    private void onOpenFile() {
        if (!confirmDiscardChanges()) {
            return;
        }
        FileChooser chooser = openImageChooser();
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        safely(() -> {
            Image image = files.read(ImageFile.of(file.toPath()));
            editor.setProject(Project.fromImage(file.getName(), RasterImage.of(image)));
            setZoom(1.0);
            setStatus("Відкрито " + file.getName());
        });
    }

    @FXML
    private void onExport() {
        Project project = editor.getProject();
        if (!compositor.hasVisibleLayers(project)
                && !Dialogs.confirm(stage, "Усі шари приховані - підсумкове зображення буде порожнім. Продовжити?")) {
            return;
        }
        FileChooser chooser = saveImageChooser(project.getName());
        Path targetPath;
        while (true) {
            File file = chooser.showSaveDialog(stage);
            if (file == null) {
                return;
            }
            Path selected = file.toPath();
            Path resolved = resolveExportPath(selected, chooser.getSelectedExtensionFilter());
            if (!resolved.equals(selected) && Files.exists(resolved)
                    && !Dialogs.confirm(stage, "Файл «" + resolved.getFileName() + "» уже існує. Перезаписати його?")) {
                if (resolved.getParent() != null && Files.isDirectory(resolved.getParent())) {
                    chooser.setInitialDirectory(resolved.getParent().toFile());
                }
                continue;
            }
            targetPath = resolved;
            break;
        }
        Path finalTargetPath = targetPath;
        safely(() -> {
            ImageFile target = ImageFile.of(finalTargetPath);
            Color background = target.format().supportsAlpha()
                    ? Color.TRANSPARENT
                    : ImageFileService.opaqueBackground(project.getBackgroundColor());
            files.write(compositor.compose(project, background), target, background);
            setStatus("Збережено " + target.path().getFileName());
        });
    }

    @FXML
    private void onExit() {
        stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
    }

    @FXML
    private void onUndo() {
        safely(editor::undo);
    }

    @FXML
    private void onRedo() {
        safely(editor::redo);
    }

    @FXML
    private void onZoomIn() {
        setZoom(canvasView.getZoom() * 1.25);
    }

    @FXML
    private void onZoomOut() {
        setZoom(canvasView.getZoom() / 1.25);
    }

    @FXML
    private void onZoomReset() {
        setZoom(1.0);
    }

    @FXML
    private void onApplyEffect(ActionEvent event) {
        String code = (String) ((MenuItem) event.getSource()).getUserData();
        applyEffect(code, Map.of());
    }

    @FXML
    private void onApplyBlur() {
        Image preview;
        try {
            preview = editor.getProject().getActiveLayer().getRendered().getImage();
        } catch (RuntimeException e) {
            showError(e);
            return;
        }
        new BlurDialog(stage, preview).showAndWait()
                .ifPresent(radius -> applyEffect(BlurEffect.CODE, Map.of(BlurEffect.PARAM_RADIUS, radius)));
    }

    private void applyEffect(String code, Map<String, Object> parameters) {
        Project project = editor.getProject();
        safely(() -> editor.execute(new ApplyEffectCommand(project, project.getActiveLayer(),
                editor.getEffects(), code, parameters)));
    }

    @FXML
    private void onSaveToServer() {
        Project project = editor.getProject();
        if (project.getServerId() == null) {
            saveNewProjectFlow(project, project.getName());
            return;
        }
        try {
            storage.save(project);
            setStatus("Проєкт «" + project.getName() + "» збережено на сервері");
        } catch (ServerUnavailableException e) {
            offerExportOnServerUnavailable(e);
        } catch (RemoteException e) {
            if (e.isVersionConflict()) {
                if (Dialogs.confirm(stage, e.getMessage() + "\n\nЗберегти вашу версію як новий проєкт?")) {
                    saveConflictedAsNewProject(project);
                }
            } else if (e.isNotFound()) {
                project.detachFromServer();
                project.markDirty();
                if (Dialogs.confirm(stage, "Цей проєкт було видалено із сервера.\nЗберегти його як новий проєкт?")) {
                    saveNewProjectFlow(project, project.getName());
                }
            } else {
                showError(e);
            }
        } catch (RuntimeException e) {
            showError(e);
        }
        refresh();
    }

    private void saveConflictedAsNewProject(Project project) {
        project.detachFromServer();
        project.markDirty();
        saveNewProjectFlow(project, project.getName() + " (копія)");
    }

    private void saveNewProjectFlow(Project project, String suggestedName) {
        String originalName = project.getName();
        String candidate = suggestedName;
        while (true) {
            var entered = promptProjectName(candidate);
            if (entered.isEmpty()) {
                project.setName(originalName);
                refresh();
                return;
            }
            candidate = entered.get();
            project.setName(candidate);
            try {
                storage.save(project);
                setStatus("Проєкт «" + project.getName() + "» збережено на сервері");
                refresh();
                return;
            } catch (ServerUnavailableException e) {
                project.setName(originalName);
                refresh();
                offerExportOnServerUnavailable(e);
                return;
            } catch (RemoteException e) {
                if ("NAME_CONFLICT".equals(e.getCode())) {
                    Dialogs.error(stage, e.getMessage());
                    continue;
                }
                project.setName(originalName);
                showError(e);
                refresh();
                return;
            } catch (RuntimeException e) {
                project.setName(originalName);
                showError(e);
                refresh();
                return;
            }
        }
    }

    private java.util.Optional<String> promptProjectName(String initialValue) {
        while (true) {
            TextInputDialog nameDialog = new TextInputDialog(initialValue);
            nameDialog.initOwner(stage);
            nameDialog.setTitle("Зберегти на сервері");
            nameDialog.setHeaderText(null);
            nameDialog.setContentText("Назва проєкту:");
            var result = nameDialog.showAndWait();
            if (result.isEmpty()) {
                return java.util.Optional.empty();
            }
            String trimmed = result.get().strip();
            if (trimmed.isEmpty()) {
                Dialogs.warning(stage, "Назва проєкту не може бути порожньою.");
                continue;
            }
            return java.util.Optional.of(trimmed);
        }
    }

    private void offerExportOnServerUnavailable(ServerUnavailableException e) {
        if (Dialogs.confirm(stage, e.getMessage() + "\n\nЕкспортувати зображення в локальний файл?")) {
            onExport();
        }
    }

    @FXML
    private void onOpenFromServer() {
        if (!confirmDiscardChanges()) {
            return;
        }
        while (true) {
            ProjectCatalogDialog dialog = new ProjectCatalogDialog(stage, storage, ProjectCatalogDialog.Mode.OPEN);
            try {
                dialog.reload();
            } catch (RuntimeException e) {
                showError(e);
                return;
            }
            var selected = dialog.showAndWait().map(ProjectSummaryDto::id);
            if (selected.isEmpty()) {
                return;
            }
            try {
                Project opened = storage.open(selected.get());
                for (Layer layer : opened.getLayers()) {
                    if (layer.isVisible()) {
                        layer.getRendered().getImage();
                    }
                }
                editor.setProject(opened);
                setZoom(1.0);
                setStatus("Відкрито проєкт «" + opened.getName() + "»");
                refresh();
                return;
            } catch (RemoteException e) {
                if (e.isNotFound()) {
                    Dialogs.warning(stage, "Проєкт не знайдено на сервері. Каталог буде оновлено.");
                    continue;
                }
                showError(e);
                return;
            } catch (RuntimeException e) {
                showError(e);
                return;
            }
        }
    }

    @FXML
    private void onManageProjects() {
        ProjectCatalogDialog dialog = new ProjectCatalogDialog(stage, storage, ProjectCatalogDialog.Mode.MANAGE);
        safely(() -> {
            dialog.reload();
            dialog.showAndWait();
        });
    }

    @FXML
    private void onAddLayer() {
        Project project = editor.getProject();
        safely(() -> editor.execute(AddLayerCommand.newLayer(project, "Шар " + (project.getLayers().size() + 1))));
    }

    @FXML
    private void onRemoveLayer() {
        Project project = editor.getProject();
        safely(() -> editor.execute(new RemoveLayerCommand(project, project.getActiveIndex())));
    }

    @FXML
    private void onDuplicateLayer() {
        safely(() -> editor.execute(AddLayerCommand.duplicateActive(editor.getProject())));
    }

    @FXML
    private void onLayerUp() {
        Project project = editor.getProject();
        int i = project.getActiveIndex();
        if (i < project.getLayers().size() - 1) {
            safely(() -> editor.execute(new MoveLayerCommand(project, i, i + 1)));
        }
    }

    @FXML
    private void onLayerDown() {
        Project project = editor.getProject();
        int i = project.getActiveIndex();
        if (i > 0) {
            safely(() -> editor.execute(new MoveLayerCommand(project, i, i - 1)));
        }
    }

    @FXML
    private void onRemoveEffect() {
        int index = effectList.getSelectionModel().getSelectedIndex();
        if (index < 0) {
            return;
        }
        Project project = editor.getProject();
        safely(() -> editor.execute(new RemoveEffectCommand(project, project.getActiveLayer(), index)));
    }

    private void onLayerSelected(int viewIndex) {
        if (refreshing || viewIndex < 0) {
            return;
        }
        Project project = editor.getProject();
        project.setActiveIndex(project.getLayers().size() - 1 - viewIndex);
        refreshLayerDetails();
    }

    private void onOpacityChanged() {
        Project project = editor.getProject();
        Layer layer = project.getActiveLayer();
        double opacity = opacitySlider.getValue() / 100.0;
        if (Math.abs(opacity - layer.getOpacity()) > 1e-9) {
            safely(() -> editor.execute(new ChangeLayerCommand(project, layer,
                    layer.getName(), layer.isVisible(), opacity)));
        }
    }

    private void onToolSelected(Toggle old, Toggle selected) {
        if (selected == null) {
            toolGroup.selectToggle(old);
            return;
        }
        Tool tool = tools.get((String) selected.getUserData());
        editor.setTool(tool);
        setStatus("Інструмент: " + tool.getName());
    }

    public void cancelTool() {
        editor.getTool().onCancel(editor);
    }

    private void onTextRequested(double x, double y) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.initOwner(stage);
        dialog.setTitle("Текст");
        dialog.setHeaderText(null);
        dialog.setContentText("Напис:");
        dialog.showAndWait()
                .filter(text -> !text.isBlank())
                .ifPresent(text -> safely(() -> editor.commit(
                        new TextLabel(editor.getPreview(), editor.getCurrentStyle(), x, y, text))));
    }

    public boolean confirmDiscardChanges() {
        return !editor.getProject().isDirty()
                || Dialogs.confirm(stage, "Поточний проєкт містить незбережені зміни. Відмовитися від них?");
    }

    private void refresh() {
        refreshing = true;
        try {
            Project project = editor.getProject();
            List<Layer> topFirst = new ArrayList<>(project.getLayers());
            Collections.reverse(topFirst);
            layerList.getItems().setAll(topFirst);
            layerList.getSelectionModel().select(topFirst.size() - 1 - project.getActiveIndex());
            removeLayerButton.setDisable(project.getLayers().size() == 1);
            refreshLayerDetails();

            undoItem.setDisable(!editor.getHistory().canUndo());
            redoItem.setDisable(!editor.getHistory().canRedo());
            undoItem.setText(editor.getHistory().nextUndoName().map(n -> "Скасувати: " + n).orElse("Скасувати"));
            redoItem.setText(editor.getHistory().nextRedoName().map(n -> "Повторити: " + n).orElse("Повторити"));
            refreshTitle();
        } finally {
            refreshing = false;
        }
        canvasView.redraw();
    }

    private void refreshLayerDetails() {
        Layer layer = editor.getProject().getActiveLayer();
        opacitySlider.setValue(layer.getOpacity() * 100);
        effectList.getItems().setAll(layer.getEffects().stream().map(Effect::getDisplayName).toList());
    }

    private void refreshTitle() {
        if (stage != null) {
            Project p = editor.getProject();
            stage.setTitle((p.isDirty() ? "*" : "") + p.getName() + " - ShufflePix");
        }
    }

    private void setZoom(double zoom) {
        canvasView.setZoom(zoom);
        zoomLabel.setText(Math.round(canvasView.getZoom() * 100) + " %");
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
    }

    private void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            showError(e);
        }
        refresh();
    }

    private void showError(RuntimeException e) {
        String message;
        if (e instanceof ServerUnavailableException) {
            message = e.getMessage() + "\nЗображення можна експортувати в локальний файл.";
        } else if (e instanceof RemoteException || e instanceof ImageFileException) {
            message = e.getMessage();
        } else if (e instanceof UnsupportedOperationException) {
            message = "Ще не реалізовано: " + e.getMessage();
        } else {
            message = "Непередбачена помилка: " + e;
        }
        Dialogs.error(stage, message);
    }

    private static FileChooser openImageChooser() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Відкрити зображення");
        List<String> allExtensions = new ArrayList<>();
        for (ImageFormat format : ImageFormat.values()) {
            for (String ext : format.getExtensions()) {
                allExtensions.add("*." + ext);
            }
        }
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Зображення (BMP, PNG, JPG)", allExtensions));
        for (ImageFormat format : ImageFormat.values()) {
            chooser.getExtensionFilters().add(extensionFilter(format));
        }
        return chooser;
    }

    private static FileChooser saveImageChooser(String projectName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Експортувати зображення");
        chooser.setInitialFileName(suggestBaseName(projectName));
        for (ImageFormat format : List.of(ImageFormat.PNG, ImageFormat.JPG, ImageFormat.BMP)) {
            chooser.getExtensionFilters().add(extensionFilter(format));
        }
        return chooser;
    }

    private static FileChooser.ExtensionFilter extensionFilter(ImageFormat format) {
        return new FileChooser.ExtensionFilter(format.getDisplayName(),
                format.getExtensions().stream().map(ext -> "*." + ext).toList());
    }

    private static Path resolveExportPath(Path path, FileChooser.ExtensionFilter selectedFilter) {
        Path fileNamePath = path.getFileName();
        if (fileNamePath == null) {
            return path;
        }
        String fileName = fileNamePath.toString();
        if (fileName.contains(".")) {
            return path;
        }
        ImageFormat format = formatFromFilter(selectedFilter);
        return path.resolveSibling(fileName + "." + format.getExtensions().get(0));
    }

    private static ImageFormat formatFromFilter(FileChooser.ExtensionFilter filter) {
        if (filter != null) {
            for (ImageFormat format : ImageFormat.values()) {
                if (format.getDisplayName().equals(filter.getDescription())) {
                    return format;
                }
            }
        }
        return ImageFormat.PNG;
    }

    private static String suggestBaseName(String projectName) {
        if (projectName == null || projectName.isBlank()) {
            return "image";
        }
        String base = projectName.strip();
        String lower = base.toLowerCase(Locale.ROOT);
        for (ImageFormat format : ImageFormat.values()) {
            for (String ext : format.getExtensions()) {
                String suffix = "." + ext;
                if (lower.endsWith(suffix) && base.length() > suffix.length()) {
                    base = base.substring(0, base.length() - suffix.length());
                    break;
                }
            }
        }
        base = base.replaceAll("[\\\\/:*?\"<>|]", "_").strip();
        return base.isEmpty() ? "image" : base;
    }

    private final class LayerCell extends ListCell<Layer> {
        private final CheckBox visible = new CheckBox();

        LayerCell() {
            visible.setOnAction(e -> {
                Layer layer = getItem();
                if (layer != null) {
                    safely(() -> editor.execute(new ChangeLayerCommand(editor.getProject(), layer,
                            layer.getName(), visible.isSelected(), layer.getOpacity())));
                }
            });
        }

        @Override
        protected void updateItem(Layer layer, boolean empty) {
            super.updateItem(layer, empty);
            if (empty || layer == null) {
                setGraphic(null);
                setText(null);
            } else {
                visible.setSelected(layer.isVisible());
                setGraphic(visible);
                setText(layer.getName());
            }
        }
    }
}
