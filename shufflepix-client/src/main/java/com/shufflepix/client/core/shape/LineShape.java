package com.shufflepix.client.core.shape;

import com.shufflepix.client.core.style.Style;

public class LineShape extends Primitive {
    private final double x1;
    private final double y1;
    private final double x2;
    private final double y2;

    public LineShape(ShapeRenderer renderer, Style style, double x1, double y1, double x2, double y2) {
        super(renderer, style);
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
    }

    @Override
    public void draw() {
        renderer.drawLine(x1, y1, x2, y2, style);
    }

    @Override
    public LineShape copy() {
        return new LineShape(renderer, style, x1, y1, x2, y2);
    }
}
