package com.shufflepix.client.ui;

import com.shufflepix.client.core.Editor;
import com.shufflepix.client.core.model.Layer;
import com.shufflepix.client.core.model.Project;
import com.shufflepix.client.core.shape.CanvasPreviewRenderer;
import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.ImagePattern;
import javafx.scene.transform.Scale;

import java.util.function.Consumer;

public class CanvasView extends StackPane {
    public static final double MIN_ZOOM = 0.1;
    public static final double MAX_ZOOM = 8.0;
    private static final int CHECKER_SIZE = 8;
    private static final ImagePattern CHECKERBOARD = createCheckerboard();

    private final Editor editor;
    private final Canvas imageCanvas = new Canvas();
    private final Canvas previewCanvas = new Canvas();
    private final Scale scale = new Scale(1, 1);
    private final Group group = new Group(imageCanvas, previewCanvas);
    private Consumer<RuntimeException> errorHandler = e -> { throw e; };
    private Runnable onHiddenLayer = () -> { };
    private double zoom = 1.0;

    public CanvasView(Editor editor) {
        this.editor = editor;
        group.getTransforms().add(scale);
        getChildren().add(new Group(group));
        editor.setPreview(new CanvasPreviewRenderer(previewCanvas.getGraphicsContext2D()));

        previewCanvas.addEventHandler(MouseEvent.MOUSE_PRESSED, this::onPressed);
        previewCanvas.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::onDragged);
        previewCanvas.addEventHandler(MouseEvent.MOUSE_RELEASED, this::onReleased);
        resizeToProject();
    }

    public void redraw() {
        Project project = editor.getProject();
        if (imageCanvas.getWidth() != project.getWidth() || imageCanvas.getHeight() != project.getHeight()) {
            resizeToProject();
        }
        GraphicsContext gc = imageCanvas.getGraphicsContext2D();
        gc.setGlobalAlpha(1.0);
        gc.setFill(CHECKERBOARD);
        gc.fillRect(0, 0, imageCanvas.getWidth(), imageCanvas.getHeight());
        for (Layer layer : project.getLayers()) {
            if (layer.isVisible()) {
                try {
                    Image rendered = layer.getRendered().getImage();
                    gc.setGlobalAlpha(layer.getOpacity());
                    gc.drawImage(rendered, 0, 0);
                } catch (RuntimeException ex) {
                    layer.setVisible(false);
                    gc.setGlobalAlpha(1.0);
                    errorHandler.accept(ex);
                }
            }
        }
        gc.setGlobalAlpha(1.0);
    }

    public double getZoom() {
        return zoom;
    }

    public void setZoom(double zoom) {
        this.zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
        scale.setX(this.zoom);
        scale.setY(this.zoom);
    }

    public void setErrorHandler(Consumer<RuntimeException> errorHandler) {
        this.errorHandler = errorHandler;
    }

    public void setOnHiddenLayer(Runnable onHiddenLayer) {
        this.onHiddenLayer = onHiddenLayer;
    }

    private void resizeToProject() {
        Project project = editor.getProject();
        imageCanvas.setWidth(project.getWidth());
        imageCanvas.setHeight(project.getHeight());
        previewCanvas.setWidth(project.getWidth());
        previewCanvas.setHeight(project.getHeight());
    }

    private void onPressed(MouseEvent e) {
        if (e.getButton() != MouseButton.PRIMARY) {
            return;
        }
        if (!editor.canDrawOnActiveLayer()) {
            onHiddenLayer.run();
            return;
        }
        safely(() -> editor.getTool().onPress(editor, e.getX(), e.getY()));
    }

    private void onDragged(MouseEvent e) {
        if (e.isPrimaryButtonDown() && editor.canDrawOnActiveLayer()) {
            safely(() -> editor.getTool().onDrag(editor, e.getX(), e.getY()));
        }
    }

    private void onReleased(MouseEvent e) {
        if (e.getButton() == MouseButton.PRIMARY && editor.canDrawOnActiveLayer()) {
            safely(() -> editor.getTool().onRelease(editor, e.getX(), e.getY()));
        }
    }

    private void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            editor.getPreview().clear();
            errorHandler.accept(ex);
        }
    }

    private static ImagePattern createCheckerboard() {
        int tile = CHECKER_SIZE * 2;
        WritableImage img = new WritableImage(tile, tile);
        PixelWriter writer = img.getPixelWriter();
        for (int y = 0; y < tile; y++) {
            for (int x = 0; x < tile; x++) {
                boolean light = ((x / CHECKER_SIZE) + (y / CHECKER_SIZE)) % 2 == 0;
                writer.setArgb(x, y, light ? 0xFFFFFFFF : 0xFFDCDCDC);
            }
        }
        return new ImagePattern(img, 0, 0, tile, tile, false);
    }
}
