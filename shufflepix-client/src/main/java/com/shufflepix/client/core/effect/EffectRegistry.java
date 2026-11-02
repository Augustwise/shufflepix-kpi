package com.shufflepix.client.core.effect;

import com.shufflepix.client.core.image.LayerImage;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

public class EffectRegistry {
    @FunctionalInterface
    public interface EffectFactory extends BiFunction<LayerImage, Map<String, Object>, Effect> {
    }

    private final Map<String, EffectFactory> factories = new LinkedHashMap<>();

    public static EffectRegistry withDefaults() {
        EffectRegistry registry = new EffectRegistry();
        registry.register(GrayscaleEffect.CODE, (src, p) -> new GrayscaleEffect(src));
        registry.register(InfraredEffect.CODE, (src, p) -> new InfraredEffect(src));
        registry.register(NegativeEffect.CODE, (src, p) -> new NegativeEffect(src));
        registry.register(SepiaEffect.CODE, (src, p) -> new SepiaEffect(src));
        registry.register(BlurEffect.CODE, (src, p) -> new BlurEffect(src,
                ((Number) p.getOrDefault(BlurEffect.PARAM_RADIUS, BlurEffect.DEFAULT_RADIUS)).intValue()));
        return registry;
    }

    public void register(String code, EffectFactory factory) {
        factories.put(code, factory);
    }

    public Effect create(String code, LayerImage source, Map<String, Object> parameters) {
        EffectFactory factory = factories.get(code);
        if (factory == null) {
            throw new IllegalArgumentException("Невідомий ефект: " + code);
        }
        return factory.apply(source, parameters == null ? Map.of() : parameters);
    }

    public Set<String> codes() {
        return factories.keySet();
    }
}
