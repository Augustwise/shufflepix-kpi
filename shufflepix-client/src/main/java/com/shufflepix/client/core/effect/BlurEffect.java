package com.shufflepix.client.core.effect;

import com.shufflepix.client.core.image.LayerImage;
import com.shufflepix.common.image.PixelEffects;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

import java.util.Map;

public class BlurEffect extends Effect {
    public static final String CODE = PixelEffects.CODE_BLUR;
    public static final String PARAM_RADIUS = PixelEffects.PARAM_RADIUS;
    public static final int DEFAULT_RADIUS = PixelEffects.DEFAULT_BLUR_RADIUS;

    private final int radius;

    public BlurEffect(LayerImage source, int radius) {
        super(source);
        if (radius < 1) {
            throw new IllegalArgumentException("Радіус розмиття має бути додатним");
        }
        this.radius = radius;
    }

    public int getRadius() {
        return radius;
    }

    @Override
    public String getCode() {
        return CODE;
    }

    @Override
    public String getDisplayName() {
        return "Розмиття (радіус " + radius + ")";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of(PARAM_RADIUS, radius);
    }

    @Override
    public Effect wrap(LayerImage newSource) {
        return new BlurEffect(newSource, radius);
    }

    @Override
    protected Image apply(Image input) {
        int width = (int) input.getWidth();
        int height = (int) input.getHeight();
        WritableImage result = new WritableImage(width, height);
        if (width <= 0 || height <= 0) {
            return result;
        }

        // Premultiplied ARGB prevents dark fringes when blurring transparent regions.
        int[] src = new int[width * height];
        int[] tmp = new int[width * height];
        input.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbPreInstance(), src, 0, width);

        PixelEffects.blurPremultiplied(src, tmp, width, height, radius);

        result.getPixelWriter().setPixels(0, 0, width, height, PixelFormat.getIntArgbPreInstance(), src, 0, width);
        return result;
    }
}
