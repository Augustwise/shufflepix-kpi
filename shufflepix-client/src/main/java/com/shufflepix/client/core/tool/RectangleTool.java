package com.shufflepix.client.core.tool;

import com.shufflepix.client.core.shape.Primitive;
import com.shufflepix.client.core.shape.RectangleShape;
import com.shufflepix.client.core.shape.ShapeRenderer;
import com.shufflepix.client.core.style.Style;

public class RectangleTool extends ShapeTool {
    @Override
    public String getName() {
        return "Прямокутник";
    }

    @Override
    protected Primitive createShape(ShapeRenderer renderer, Style style, double x1, double y1, double x2, double y2) {
        return RectangleShape.between(renderer, style, x1, y1, x2, y2);
    }
}
