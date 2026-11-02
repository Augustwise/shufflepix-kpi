package com.shufflepix.client.core.effect;

import com.shufflepix.client.core.image.LayerImage;
import com.shufflepix.common.image.PixelEffects;
import javafx.scene.image.Image;

public class NegativeEffect extends Effect {
    public static final String CODE = PixelEffects.CODE_NEGATIVE;

    public NegativeEffect(LayerImage source) {
        super(source);
    }

    @Override
    public String getCode() {
        return CODE;
    }

    @Override
    public String getDisplayName() {
        return "Негатив";
    }

    @Override
    public Effect wrap(LayerImage newSource) {
        return new NegativeEffect(newSource);
    }

    @Override
    protected Image apply(Image input) {
        return transformPixels(input, PixelEffects::negativePixel);
    }
}
