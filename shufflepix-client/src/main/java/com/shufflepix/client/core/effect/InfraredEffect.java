package com.shufflepix.client.core.effect;

import com.shufflepix.client.core.image.LayerImage;
import com.shufflepix.common.image.PixelEffects;
import javafx.scene.image.Image;

public class InfraredEffect extends Effect {
    public static final String CODE = PixelEffects.CODE_INFRARED;

    public InfraredEffect(LayerImage source) {
        super(source);
    }

    @Override
    public String getCode() {
        return CODE;
    }

    @Override
    public String getDisplayName() {
        return "Інфрачервоний";
    }

    @Override
    public Effect wrap(LayerImage newSource) {
        return new InfraredEffect(newSource);
    }

    @Override
    protected Image apply(Image input) {
        return transformPixels(input, PixelEffects::infraredPixel);
    }
}
