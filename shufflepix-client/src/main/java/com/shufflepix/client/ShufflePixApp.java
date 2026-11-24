package com.shufflepix.client;

import com.shufflepix.client.config.ClientConfig;
import com.shufflepix.client.core.Editor;
import com.shufflepix.client.io.ImageFileService;
import com.shufflepix.client.remote.LayerServiceProxy;
import com.shufflepix.client.remote.ProjectServiceProxy;
import com.shufflepix.client.remote.ProjectStorage;
import com.shufflepix.client.remote.RestClient;
import com.shufflepix.client.remote.ThumbnailServiceProxy;
import com.shufflepix.client.ui.MainController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

public class ShufflePixApp extends Application {
    public static final String DEMO_ARG = "--demo";
    public static final String DEMO_PROPERTY = "shufflepix.demo";

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws IOException {
        ClientConfig config = ClientConfig.load();
        Editor editor = new Editor();
        if (isDemoMode()) {
            editor.setProject(DemoProjectFactory.create());
        }
        ImageFileService files = new ImageFileService();

        RestClient rest = new RestClient(config);
        LayerServiceProxy layers = new LayerServiceProxy(rest);
        ProjectStorage storage = new ProjectStorage(new ProjectServiceProxy(rest), layers,
                new ThumbnailServiceProxy(rest), files, editor.getEffects());

        FXMLLoader loader = new FXMLLoader(getClass().getResource("ui/main-view.fxml"));
        loader.setControllerFactory(type -> {
            if (type == MainController.class) {
                return new MainController(editor, files, storage);
            }
            throw new IllegalArgumentException("Невідомий контролер: " + type);
        });
        Parent root = loader.load();
        MainController controller = loader.getController();

        Scene scene = new Scene(root);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("ui/styles.css")).toExternalForm());
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                controller.cancelTool();
            }
        });

        stage.setTitle("ShufflePix");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            if (!controller.confirmDiscardChanges()) {
                e.consume();
            }
        });
        controller.setStage(stage);
        stage.show();
    }

    private boolean isDemoMode() {
        Parameters params = getParameters();
        return Boolean.getBoolean(DEMO_PROPERTY)
                || (params != null && params.getRaw().contains(DEMO_ARG));
    }
}
