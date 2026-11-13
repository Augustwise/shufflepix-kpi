package com.shufflepix.client.core.tool;

public class TextTool implements Tool {
    @Override
    public String getName() {
        return "Текст";
    }

    @Override
    public void onPress(ToolContext ctx, double x, double y) {
        ctx.requestTextInput(x, y);
    }

    @Override
    public void onDrag(ToolContext ctx, double x, double y) {
    }

    @Override
    public void onRelease(ToolContext ctx, double x, double y) {
    }
}
