package com.shufflepix.client.core;

import com.shufflepix.client.core.image.RasterImage;
import com.shufflepix.client.core.shape.CanvasPreviewRenderer;
import com.shufflepix.client.core.shape.EllipseShape;
import com.shufflepix.client.core.shape.FreehandStroke;
import com.shufflepix.client.core.shape.LayerPixelRenderer;
import com.shufflepix.client.core.shape.LineShape;
import com.shufflepix.client.core.shape.RectangleShape;
import com.shufflepix.client.core.shape.TextLabel;
import com.shufflepix.client.core.style.Color;
import com.shufflepix.client.core.style.Style;
import com.shufflepix.client.core.style.StyleFactory;
import com.shufflepix.client.core.tool.RectangleTool;
import javafx.scene.image.PixelReader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayerPixelRendererTest {
    private static final Color RED = new Color(255, 0, 0, 255);
    private static final Color GREEN = new Color(0, 200, 0, 255);
    private static final Color BLUE = new Color(0, 0, 255, 255);

    private final StyleFactory styles = new StyleFactory();

    @Test
    void drawsLineOnTransparentLayer() {
        RasterImage image = RasterImage.filled(100, 100, Color.TRANSPARENT);
        Style style = styles.get(RED, null, 4, Editor.DEFAULT_FONT, 16);

        new LayerPixelRenderer(image).drawLine(10, 50, 90, 50, style);

        PixelReader reader = image.getImage().getPixelReader();
        assertEquals(RED.toArgb(), reader.getArgb(50, 50));
        assertEquals(Color.TRANSPARENT.toArgb(), reader.getArgb(50, 10));
    }

    @Test
    void drawsFilledAndUnfilledRectangles() {
        RasterImage unfilled = RasterImage.filled(100, 100, Color.TRANSPARENT);
        Style strokeOnly = styles.get(RED, null, 4, Editor.DEFAULT_FONT, 16);
        new LayerPixelRenderer(unfilled).drawRectangle(20, 20, 60, 60, strokeOnly);

        PixelReader unfilledReader = unfilled.getImage().getPixelReader();
        assertEquals(RED.toArgb(), unfilledReader.getArgb(50, 20));
        assertEquals(Color.TRANSPARENT.toArgb(), unfilledReader.getArgb(50, 50));

        RasterImage filled = RasterImage.filled(100, 100, Color.TRANSPARENT);
        Style withFill = styles.get(RED, BLUE, 4, Editor.DEFAULT_FONT, 16);
        RectangleShape.between(new LayerPixelRenderer(filled), withFill, 80, 80, 20, 20).draw();

        PixelReader filledReader = filled.getImage().getPixelReader();
        assertEquals(RED.toArgb(), filledReader.getArgb(50, 20));
        assertEquals(BLUE.toArgb(), filledReader.getArgb(50, 50));
    }

    @Test
    void drawsFilledEllipse() {
        RasterImage image = RasterImage.filled(100, 100, Color.TRANSPARENT);
        Style style = styles.get(RED, GREEN, 4, Editor.DEFAULT_FONT, 16);

        EllipseShape.between(new LayerPixelRenderer(image), style, 10, 20, 90, 80).draw();

        PixelReader reader = image.getImage().getPixelReader();
        assertEquals(GREEN.toArgb(), reader.getArgb(50, 50));
        assertEquals(Color.TRANSPARENT.toArgb(), reader.getArgb(12, 22));
    }

    @Test
    void drawsPolylineAndSinglePointClick() {
        RasterImage image = RasterImage.filled(100, 100, Color.TRANSPARENT);
        Style style = styles.get(BLUE, null, 6, Editor.DEFAULT_FONT, 16);
        LayerPixelRenderer renderer = new LayerPixelRenderer(image);

        renderer.drawPolyline(new double[]{20, 50, 80}, new double[]{30, 30, 70}, style);
        renderer.drawPolyline(new double[]{15}, new double[]{85}, style);

        PixelReader reader = image.getImage().getPixelReader();
        assertEquals(BLUE.toArgb(), reader.getArgb(35, 30));
        assertEquals(BLUE.toArgb(), reader.getArgb(15, 85));
    }

    @Test
    void erasesPixelsToTransparent() {
        RasterImage image = RasterImage.filled(100, 100, Color.WHITE);
        LayerPixelRenderer renderer = new LayerPixelRenderer(image);

        renderer.erasePolyline(new double[]{10, 90}, new double[]{50, 50}, 10);

        PixelReader reader = image.getImage().getPixelReader();
        assertEquals(0, (reader.getArgb(50, 50) >>> 24) & 0xFF);
        assertEquals(Color.WHITE.toArgb(), reader.getArgb(50, 10));
    }

    @Test
    void drawsTextWithSystemFont() {
        RasterImage image = RasterImage.filled(200, 80, Color.WHITE);
        Style style = styles.get(Color.BLACK, null, 2, Editor.DEFAULT_FONT, 28);

        new TextLabel(new LayerPixelRenderer(image), style, 10, 50, "ShufflePix").draw();

        PixelReader reader = image.getImage().getPixelReader();
        int nonWhitePixels = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (reader.getArgb(x, y) != Color.WHITE.toArgb()) {
                    nonWhitePixels++;
                }
            }
        }
        assertTrue(nonWhitePixels > 50, "Очікувалися непорожні пікселі тексту, знайдено: " + nonWhitePixels);
    }

    @Test
    void editorCommitUndoAndRedoRestoreLayerPixels() {
        Editor editor = new Editor();
        editor.setStrokeColor(RED);
        editor.setLineWidth(6);

        LineShape line = new LineShape(null, editor.getCurrentStyle(), 20, 40, 120, 40);
        editor.commit(line);

        PixelReader afterDraw = editor.getProject().getActiveLayer().getContent().getImage().getPixelReader();
        assertEquals(RED.toArgb(), afterDraw.getArgb(70, 40));

        editor.undo();
        PixelReader afterUndo = editor.getProject().getActiveLayer().getContent().getImage().getPixelReader();
        assertEquals(Color.WHITE.toArgb(), afterUndo.getArgb(70, 40));

        editor.redo();
        PixelReader afterRedo = editor.getProject().getActiveLayer().getContent().getImage().getPixelReader();
        assertEquals(RED.toArgb(), afterRedo.getArgb(70, 40));
    }

    @Test
    void freehandEraserThroughEditorMakesLayerTransparent() {
        Editor editor = new Editor();
        editor.setLineWidth(8);

        FreehandStroke stroke = new FreehandStroke(null, editor.getEraserStyle(), true);
        stroke.addPoint(30, 30);
        stroke.addPoint(90, 30);
        editor.commit(stroke);

        PixelReader reader = editor.getProject().getActiveLayer().getContent().getImage().getPixelReader();
        assertNotEquals(Color.WHITE.toArgb(), reader.getArgb(60, 30));
        assertEquals(0, (reader.getArgb(60, 30) >>> 24) & 0xFF);
    }

    @Test
    void cancellingShapeToolBeforeReleaseDoesNotModifyLayer() {
        Editor editor = new Editor();
        editor.setPreview(new CanvasPreviewRenderer(null) {
            @Override public void clear() { }
            @Override public void drawRectangle(double x, double y, double w, double h, Style s) { }
        });
        editor.setStrokeColor(RED);

        RectangleTool tool = new RectangleTool();
        tool.onPress(editor, 10, 10);
        tool.onDrag(editor, 80, 80);
        tool.onCancel(editor);
        tool.onRelease(editor, 80, 80);

        assertFalse(editor.getHistory().canUndo());
        assertFalse(editor.getProject().isDirty());
        PixelReader reader = editor.getProject().getActiveLayer().getContent().getImage().getPixelReader();
        assertEquals(Color.WHITE.toArgb(), reader.getArgb(10, 10));
    }
}
