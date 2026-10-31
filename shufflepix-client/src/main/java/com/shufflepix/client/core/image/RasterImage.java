package com.shufflepix.client.core.image;

import com.shufflepix.client.core.style.Color;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

public class RasterImage implements LayerImage {
    private final WritableImage pixels;

    public RasterImage(WritableImage pixels) {
        this.pixels = pixels;
    }

    public static RasterImage of(Image image) {
        return new RasterImage(new WritableImage(image.getPixelReader(),
                (int) image.getWidth(), (int) image.getHeight()));
    }

    public static RasterImage filled(int width, int height, Color color) {
        WritableImage image = new WritableImage(width, height);
        if (color.alpha() != 0) {
            PixelWriter writer = image.getPixelWriter();
            int argb = color.toArgb();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    writer.setArgb(x, y, argb);
                }
            }
        }
        return new RasterImage(image);
    }

    public WritableImage getPixels() {
        return pixels;
    }

    @Override
    public Image getImage() {
        return pixels;
    }

    @Override
    public int getWidth() {
        return (int) pixels.getWidth();
    }

    @Override
    public int getHeight() {
        return (int) pixels.getHeight();
    }

    @Override
    public RasterImage copy() {
        return of(pixels);
    }
}
