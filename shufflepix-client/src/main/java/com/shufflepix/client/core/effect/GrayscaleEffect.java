package com.shufflepix.client.core.effect;

import com.shufflepix.client.core.image.LayerImage;
import com.shufflepix.common.image.PixelEffects;
import javafx.scene.image.Image;

public class GrayscaleEffect extends Effect {
    public static final String CODE = PixelEffects.CODE_GRAYSCALE;

    public GrayscaleEffect(LayerImage source) {
        super(source);
    }

    @Override
    public String getCode() {
        return CODE;
    }

    @Override
    public String getDisplayName() {
        return "Чорно-білий";
    }

    @Override
    public Effect wrap(LayerImage newSource) {
        return new GrayscaleEffect(newSource);
    }

    @Override
    protected Image apply(Image input) {
        return transformPixels(input, PixelEffects::grayscalePixel);
    }
}
