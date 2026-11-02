package com.shufflepix.client.core.effect;

import com.shufflepix.client.core.image.LayerImage;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

import java.util.Map;
import java.util.function.IntUnaryOperator;

public abstract class Effect implements LayerImage {
    protected final LayerImage source;
    private Image cachedInput;
    private Image cachedOutput;

    protected Effect(LayerImage source) {
        this.source = source;
    }

    public abstract String getCode();

    public abstract String getDisplayName();

    public Map<String, Object> getParameters() {
        return Map.of();
    }

    public abstract Effect wrap(LayerImage newSource);

    protected abstract Image apply(Image input);

    public LayerImage getSource() {
        return source;
    }

    @Override
    public Image getImage() {
        Image input = source.getImage();
        if (cachedOutput == null || cachedInput != input) {
            cachedOutput = apply(input);
            cachedInput = input;
        }
        return cachedOutput;
    }

    @Override
    public int getWidth() {
        return source.getWidth();
    }

    @Override
    public int getHeight() {
        return source.getHeight();
    }

    @Override
    public Effect copy() {
        return wrap(source.copy());
    }

    protected static WritableImage transformPixels(Image input, IntUnaryOperator transform) {
        int width = (int) input.getWidth();
        int height = (int) input.getHeight();
        WritableImage result = new WritableImage(width, height);
        if (width <= 0 || height <= 0) {
            return result;
        }
        int[] pixels = new int[width * height];
        input.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
        for (int i = 0; i < pixels.length; i++) {
            int argb = pixels[i];
            if ((argb >>> 24) == 0) {
                continue;
            }
            pixels[i] = transform.applyAsInt(argb);
        }
        result.getPixelWriter().setPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
        return result;
    }

    protected static int clamp(int value) {
        return value < 0 ? 0 : Math.min(255, value);
    }
}
