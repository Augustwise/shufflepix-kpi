package com.shufflepix.client.core.shape;

import com.shufflepix.client.core.style.Style;

public interface ShapeRenderer {
    void drawLine(double x1, double y1, double x2, double y2, Style style);

    void drawRectangle(double x, double y, double width, double height, Style style);

    void drawEllipse(double x, double y, double width, double height, Style style);

    void drawPolyline(double[] xs, double[] ys, Style style);

    void drawText(double x, double y, String text, Style style);

    void erasePolyline(double[] xs, double[] ys, double width);
}
