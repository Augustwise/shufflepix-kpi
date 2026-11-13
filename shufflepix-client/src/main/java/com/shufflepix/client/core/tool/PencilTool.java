package com.shufflepix.client.core.tool;

import com.shufflepix.client.core.shape.FreehandStroke;
import com.shufflepix.client.core.style.Style;

public class PencilTool implements Tool {
    private final boolean eraser;
    private FreehandStroke stroke;

    private PencilTool(boolean eraser) {
        this.eraser = eraser;
    }

    public static PencilTool pencil() {
        return new PencilTool(false);
    }

    public static PencilTool eraser() {
        return new PencilTool(true);
    }

    @Override
    public String getName() {
        return eraser ? "Гумка" : "Олівець";
    }

    @Override
    public void onPress(ToolContext ctx, double x, double y) {
        Style style = eraser ? ctx.getEraserStyle() : ctx.getCurrentStyle();
        stroke = new FreehandStroke(ctx.getPreview(), style, eraser);
        stroke.addPoint(x, y);
    }

    @Override
    public void onDrag(ToolContext ctx, double x, double y) {
        if (stroke == null) {
            return;
        }
        stroke.addPoint(x, y);
        ctx.getPreview().clear();
        stroke.draw();
    }

    @Override
    public void onRelease(ToolContext ctx, double x, double y) {
        if (stroke == null) {
            return;
        }
        stroke.addPoint(x, y);
        ctx.getPreview().clear();
        ctx.commit(stroke);
        stroke = null;
    }

    @Override
    public void onCancel(ToolContext ctx) {
        stroke = null;
        Tool.super.onCancel(ctx);
    }
}
