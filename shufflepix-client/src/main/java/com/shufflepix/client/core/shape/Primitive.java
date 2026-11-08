package com.shufflepix.client.core.shape;

import com.shufflepix.client.core.model.Prototype;
import com.shufflepix.client.core.style.Style;

public abstract class Primitive implements Prototype<Primitive> {
    protected ShapeRenderer renderer;
    protected final Style style;

    protected Primitive(ShapeRenderer renderer, Style style) {
        this.renderer = renderer;
        this.style = style;
    }

    public abstract void draw();

    public void setRenderer(ShapeRenderer renderer) {
        this.renderer = renderer;
    }

    public Style getStyle() {
        return style;
    }
}
