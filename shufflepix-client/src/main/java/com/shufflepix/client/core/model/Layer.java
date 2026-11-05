package com.shufflepix.client.core.model;

import com.shufflepix.client.core.effect.Effect;
import com.shufflepix.client.core.effect.EffectRegistry;
import com.shufflepix.client.core.image.LayerImage;
import com.shufflepix.client.core.image.RasterImage;
import com.shufflepix.client.core.style.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Layer implements Prototype<Layer> {
    private UUID serverId;
    private String name;
    private boolean visible = true;
    private double opacity = 1.0;
    private LayerImage content;
    private final List<Effect> effects = new ArrayList<>();
    private boolean contentChanged = true;

    public Layer(String name, LayerImage content) {
        this.name = name;
        this.content = content;
    }

    public static Layer transparent(String name, int width, int height) {
        return new Layer(name, RasterImage.filled(width, height, Color.TRANSPARENT));
    }

    public LayerImage getRendered() {
        return effects.isEmpty() ? content : effects.get(effects.size() - 1);
    }

    public Effect applyEffect(EffectRegistry registry, String code, Map<String, Object> parameters) {
        Effect effect = registry.create(code, getRendered(), parameters);
        effects.add(effect);
        return effect;
    }

    public Effect removeEffect(int index) {
        Effect removed = effects.remove(index);
        rebuildEffects();
        return removed;
    }

    public void insertEffect(int index, Effect effect) {
        effects.add(index, effect);
        rebuildEffects();
    }

    private void rebuildEffects() {
        LayerImage source = content;
        for (int i = 0; i < effects.size(); i++) {
            Effect rebuilt = effects.get(i).wrap(source);
            effects.set(i, rebuilt);
            source = rebuilt;
        }
    }

    @Override
    public Layer copy() {
        Layer copy = new Layer(name + " (копія)", content.copy());
        copy.visible = visible;
        copy.opacity = opacity;
        copy.effects.addAll(effects);
        copy.rebuildEffects();
        return copy;
    }

    public UUID getServerId() {
        return serverId;
    }

    public void setServerId(UUID serverId) {
        this.serverId = serverId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public double getOpacity() {
        return opacity;
    }

    public void setOpacity(double opacity) {
        if (opacity < 0 || opacity > 1) {
            throw new IllegalArgumentException("Непрозорість має бути від 0 до 1");
        }
        this.opacity = opacity;
    }

    public LayerImage getContent() {
        return content;
    }

    public void setContent(LayerImage content) {
        this.content = content;
        this.contentChanged = true;
        rebuildEffects();
    }

    public List<Effect> getEffects() {
        return Collections.unmodifiableList(effects);
    }

    public boolean isContentChanged() {
        return contentChanged;
    }

    public void markContentChanged() {
        this.contentChanged = true;
    }

    public void markSaved(UUID serverId) {
        this.serverId = serverId;
        this.contentChanged = false;
    }

    @Override
    public String toString() {
        return name;
    }
}
