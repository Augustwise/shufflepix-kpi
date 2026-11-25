package com.shufflepix.client.remote;

import com.shufflepix.client.core.image.LayerImage;
import com.shufflepix.client.core.image.RasterImage;
import com.shufflepix.client.io.ImageFileException;
import com.shufflepix.client.io.ImageFileService;
import javafx.scene.image.Image;

import java.util.Objects;
import java.util.UUID;

public class RemoteImageProxy implements LayerImage {
    private final UUID layerId;
    private final int width;
    private final int height;
    private final LayerService layerService;
    private final ImageFileService images;
    private Image loaded;

    public RemoteImageProxy(UUID layerId, int width, int height, LayerService layerService) {
        this(layerId, width, height, layerService, new ImageFileService());
    }

    public RemoteImageProxy(UUID layerId, int width, int height,
                            LayerService layerService, ImageFileService images) {
        this.layerId = Objects.requireNonNull(layerId, "layerId");
        this.width = width;
        this.height = height;
        this.layerService = Objects.requireNonNull(layerService, "layerService");
        this.images = Objects.requireNonNull(images, "images");
    }

    @Override
    public Image getImage() {
        if (loaded == null) {
            byte[] png = layerService.getLayerContent(layerId);
            Image decoded;
            try {
                decoded = images.decodePng(png);
            } catch (ImageFileException e) {
                throw new RemoteException(0, "BAD_IMAGE",
                        "Не вдалося прочитати вміст шару, отриманий із сервера: " + e.getMessage());
            }
            if ((int) decoded.getWidth() != width || (int) decoded.getHeight() != height) {
                throw new RemoteException(0, "BAD_IMAGE",
                        "Розміри зображення шару (" + (int) decoded.getWidth() + "×" + (int) decoded.getHeight()
                                + ") не збігаються з розмірами проєкту (" + width + "×" + height + ")");
            }
            loaded = decoded;
        }
        return loaded;
    }

    public boolean isLoaded() {
        return loaded != null;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public RasterImage copy() {
        return RasterImage.of(getImage());
    }
}
