package com.shufflepix.client.core.shape;

import com.shufflepix.client.core.style.Style;

public class TextLabel extends Primitive {
    private final double x;
    private final double y;
    private final String text;

    public TextLabel(ShapeRenderer renderer, Style style, double x, double y, String text) {
        super(renderer, style);
        this.x = x;
        this.y = y;
        this.text = text;
    }

    @Override
    public void draw() {
        renderer.drawText(x, y, text, style);
    }

    @Override
    public TextLabel copy() {
        return new TextLabel(renderer, style, x, y, text);
    }
}
