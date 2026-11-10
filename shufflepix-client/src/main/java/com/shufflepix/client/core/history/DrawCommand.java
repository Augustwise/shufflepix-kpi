package com.shufflepix.client.core.history;

import com.shufflepix.client.core.image.LayerImage;
import com.shufflepix.client.core.image.RasterImage;
import com.shufflepix.client.core.model.Layer;
import com.shufflepix.client.core.shape.LayerPixelRenderer;
import com.shufflepix.client.core.shape.Primitive;

public class DrawCommand implements Command {
    private final Layer layer;
    private final Primitive primitive;
    private LayerImage before;
    private LayerImage after;

    public DrawCommand(Layer layer, Primitive primitive) {
        this.layer = layer;
        this.primitive = primitive;
    }

    @Override
    public String getName() {
        return "Малювання";
    }

    @Override
    public void execute() {
        before = layer.getContent();
        RasterImage target = (RasterImage) before.copy();
        primitive.setRenderer(new LayerPixelRenderer(target));
        primitive.draw();
        after = target;
        layer.setContent(after);
    }

    @Override
    public void redo() {
        layer.setContent(after);
    }

    @Override
    public void undo() {
        layer.setContent(before);
    }
}
