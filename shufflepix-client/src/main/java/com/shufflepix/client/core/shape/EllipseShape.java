package com.shufflepix.client.core.shape;

import com.shufflepix.client.core.style.Style;

public class EllipseShape extends Primitive {
    private final double x;
    private final double y;
    private final double width;
    private final double height;

    public EllipseShape(ShapeRenderer renderer, Style style, double x, double y, double width, double height) {
        super(renderer, style);
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public static EllipseShape between(ShapeRenderer renderer, Style style,
                                       double x1, double y1, double x2, double y2) {
        return new EllipseShape(renderer, style, Math.min(x1, x2), Math.min(y1, y2),
                Math.abs(x2 - x1), Math.abs(y2 - y1));
    }

    @Override
    public void draw() {
        renderer.drawEllipse(x, y, width, height, style);
    }

    @Override
    public EllipseShape copy() {
        return new EllipseShape(renderer, style, x, y, width, height);
    }
}
