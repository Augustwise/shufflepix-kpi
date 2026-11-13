package com.shufflepix.client.core.tool;

import com.shufflepix.client.core.shape.EllipseShape;
import com.shufflepix.client.core.shape.Primitive;
import com.shufflepix.client.core.shape.ShapeRenderer;
import com.shufflepix.client.core.style.Style;

public class EllipseTool extends ShapeTool {
    @Override
    public String getName() {
        return "Еліпс";
    }

    @Override
    protected Primitive createShape(ShapeRenderer renderer, Style style, double x1, double y1, double x2, double y2) {
        return EllipseShape.between(renderer, style, x1, y1, x2, y2);
    }
}
