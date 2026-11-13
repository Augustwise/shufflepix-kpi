package com.shufflepix.client.core.tool;

public interface Tool {
    String getName();

    void onPress(ToolContext ctx, double x, double y);

    void onDrag(ToolContext ctx, double x, double y);

    void onRelease(ToolContext ctx, double x, double y);

    default void onCancel(ToolContext ctx) {
        ctx.getPreview().clear();
    }
}
