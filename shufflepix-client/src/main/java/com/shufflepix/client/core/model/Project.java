package com.shufflepix.client.core.model;

import com.shufflepix.client.core.image.LayerImage;
import com.shufflepix.client.core.image.RasterImage;
import com.shufflepix.client.core.style.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Project {
    public static final String DEFAULT_NAME = "Без назви";

    private String name = DEFAULT_NAME;
    private final int width;
    private final int height;
    private final Color backgroundColor;
    private final List<Layer> layers = new ArrayList<>();
    private int activeIndex;

    private UUID serverId;
    private Long serverVersion;
    private boolean dirty;

    public Project(int width, int height, Color backgroundColor) {
        this.width = width;
        this.height = height;
        this.backgroundColor = backgroundColor;
    }

    public static Project blank(int width, int height, Color background) {
        Project project = new Project(width, height, background);
        project.addLayer(0, new Layer("Фон", RasterImage.filled(width, height, background)));
        project.dirty = false;
        return project;
    }

    public static Project fromImage(String name, LayerImage image) {
        Project project = new Project(image.getWidth(), image.getHeight(), Color.WHITE);
        project.name = name;
        project.addLayer(0, new Layer("Фон", image));
        project.dirty = false;
        return project;
    }

    public void addLayer(int index, Layer layer) {
        layers.add(index, layer);
        activeIndex = index;
        dirty = true;
    }

    public Layer removeLayer(int index) {
        if (layers.size() == 1) {
            throw new IllegalStateException("Проєкт повинен містити щонайменше один шар");
        }
        Layer removed = layers.remove(index);
        activeIndex = Math.min(index, layers.size() - 1);
        dirty = true;
        return removed;
    }

    public void moveLayer(int from, int to) {
        Layer layer = layers.remove(from);
        layers.add(to, layer);
        activeIndex = to;
        dirty = true;
    }

    public int indexOf(Layer layer) {
        return layers.indexOf(layer);
    }

    public List<Layer> getLayers() {
        return Collections.unmodifiableList(layers);
    }

    public Layer getActiveLayer() {
        return layers.get(activeIndex);
    }

    public int getActiveIndex() {
        return activeIndex;
    }

    public void setActiveIndex(int activeIndex) {
        if (activeIndex < 0 || activeIndex >= layers.size()) {
            throw new IndexOutOfBoundsException(activeIndex);
        }
        this.activeIndex = activeIndex;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public Color getBackgroundColor() {
        return backgroundColor;
    }

    public UUID getServerId() {
        return serverId;
    }

    public Long getServerVersion() {
        return serverVersion;
    }

    public void markSaved(UUID serverId, long serverVersion) {
        this.serverId = serverId;
        this.serverVersion = serverVersion;
        this.dirty = false;
    }

    public void detachFromServer() {
        this.serverId = null;
        this.serverVersion = null;
        layers.forEach(l -> l.setServerId(null));
        layers.forEach(Layer::markContentChanged);
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void markClean() {
        this.dirty = false;
    }
}
