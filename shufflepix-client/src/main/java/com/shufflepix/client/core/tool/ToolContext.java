package com.shufflepix.client.core.tool;

import com.shufflepix.client.core.model.Project;
import com.shufflepix.client.core.shape.CanvasPreviewRenderer;
import com.shufflepix.client.core.shape.Primitive;
import com.shufflepix.client.core.style.Style;

public interface ToolContext {
    Project getProject();

    Style getCurrentStyle();

    Style getEraserStyle();

    CanvasPreviewRenderer getPreview();

    void commit(Primitive primitive);

    void requestTextInput(double x, double y);
}
