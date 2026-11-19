package com.shufflepix.client.remote;

import com.shufflepix.client.core.image.LayerImage;
import com.shufflepix.client.core.image.RasterImage;
import javafx.scene.image.Image;

import java.io.ByteArrayInputStream;
import java.util.UUID;

public class RemoteImageProxy implements LayerImage {
    private final UUID layerId;
    private final int width;
    private final int height;
    private final LayerService layerService;
    private Image loaded;

    public RemoteImageProxy(UUID layerId, int width, int height, LayerService layerService) {
        this.layerId = layerId;
        this.width = width;
        this.height = height;
        this.layerService = layerService;
    }

    @Override
    public Image getImage() {
        if (loaded == null) {
            byte[] png = layerService.getLayerContent(layerId);
            loaded = new Image(new ByteArrayInputStream(png));
            if (loaded.isError()) {
                throw new RemoteException(0, "BAD_IMAGE", "Не вдалося прочитати вміст шару, отриманий із сервера");
            }
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
