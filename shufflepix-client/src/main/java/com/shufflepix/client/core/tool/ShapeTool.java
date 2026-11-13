package com.shufflepix.client.core.tool;

import com.shufflepix.client.core.shape.Primitive;
import com.shufflepix.client.core.shape.ShapeRenderer;
import com.shufflepix.client.core.style.Style;

public abstract class ShapeTool implements Tool {
    private double startX;
    private double startY;
    private Primitive current;
    private boolean active;

    protected abstract Primitive createShape(ShapeRenderer renderer, Style style,
                                             double x1, double y1, double x2, double y2);

    @Override
    public void onPress(ToolContext ctx, double x, double y) {
        startX = x;
        startY = y;
        current = null;
        active = true;
    }

    @Override
    public void onDrag(ToolContext ctx, double x, double y) {
        if (!active) {
            return;
        }
        current = createShape(ctx.getPreview(), ctx.getCurrentStyle(), startX, startY, x, y);
        ctx.getPreview().clear();
        current.draw();
    }

    @Override
    public void onRelease(ToolContext ctx, double x, double y) {
        if (!active) {
            return;
        }
        onDrag(ctx, x, y);
        active = false;
        ctx.getPreview().clear();
        ctx.commit(current);
        current = null;
    }

    @Override
    public void onCancel(ToolContext ctx) {
        active = false;
        current = null;
        Tool.super.onCancel(ctx);
    }
}
