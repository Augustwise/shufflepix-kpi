package com.shufflepix.client.core.shape;

import com.shufflepix.client.core.style.Style;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;

public class CanvasPreviewRenderer implements ShapeRenderer {
    private final GraphicsContext gc;

    public CanvasPreviewRenderer(GraphicsContext gc) {
        this.gc = gc;
    }

    public void clear() {
        gc.clearRect(0, 0, gc.getCanvas().getWidth(), gc.getCanvas().getHeight());
    }

    @Override
    public void drawLine(double x1, double y1, double x2, double y2, Style style) {
        applyStroke(style);
        gc.strokeLine(x1, y1, x2, y2);
    }

    @Override
    public void drawRectangle(double x, double y, double width, double height, Style style) {
        if (style.isFilled()) {
            gc.setFill(style.getFillColor().toFx());
            gc.fillRect(x, y, width, height);
        }
        applyStroke(style);
        gc.strokeRect(x, y, width, height);
    }

    @Override
    public void drawEllipse(double x, double y, double width, double height, Style style) {
        if (style.isFilled()) {
            gc.setFill(style.getFillColor().toFx());
            gc.fillOval(x, y, width, height);
        }
        applyStroke(style);
        gc.strokeOval(x, y, width, height);
    }

    @Override
    public void drawPolyline(double[] xs, double[] ys, Style style) {
        applyStroke(style);
        gc.strokePolyline(xs, ys, xs.length);
    }

    @Override
    public void drawText(double x, double y, String text, Style style) {
        gc.setFont(Font.font(style.getFontFamily(), style.getFontSize()));
        gc.setFill(style.getStrokeColor().toFx());
        gc.fillText(text, x, y);
    }

    @Override
    public void erasePolyline(double[] xs, double[] ys, double width) {
        gc.setStroke(javafx.scene.paint.Color.rgb(128, 128, 128, 0.5));
        gc.setLineWidth(width);
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.strokePolyline(xs, ys, xs.length);
    }

    private void applyStroke(Style style) {
        gc.setStroke(style.getStrokeColor().toFx());
        gc.setLineWidth(style.getLineWidth());
        gc.setLineCap(StrokeLineCap.ROUND);
    }
}
