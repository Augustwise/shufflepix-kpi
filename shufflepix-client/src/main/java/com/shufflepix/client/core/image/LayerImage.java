package com.shufflepix.client.core.image;

import javafx.scene.image.Image;

public interface LayerImage {
    Image getImage();

    int getWidth();

    int getHeight();

    LayerImage copy();
}
