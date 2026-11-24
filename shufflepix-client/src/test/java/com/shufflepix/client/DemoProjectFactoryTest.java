package com.shufflepix.client;

import com.shufflepix.client.core.effect.BlurEffect;
import com.shufflepix.client.core.effect.EffectRegistry;
import com.shufflepix.client.core.effect.InfraredEffect;
import com.shufflepix.client.core.model.Layer;
import com.shufflepix.client.core.model.Project;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoProjectFactoryTest {
    @Test
    void createsCleanMultiLayerDemoProject() {
        Project project = DemoProjectFactory.create();

        assertEquals(DemoProjectFactory.WIDTH, project.getWidth());
        assertEquals(DemoProjectFactory.HEIGHT, project.getHeight());
        assertFalse(project.isDirty());
        assertEquals(4, project.getLayers().size());
        assertEquals(0, project.getActiveIndex());

        Layer landscape = project.getLayers().get(0);
        Layer shapes = project.getLayers().get(1);
        Layer text = project.getLayers().get(2);
        Layer hidden = project.getLayers().get(3);

        assertTrue(landscape.isVisible());
        assertTrue(shapes.isVisible());
        assertTrue(text.isVisible());
        assertFalse(hidden.isVisible());

        // Перевірка, що ефекти коректно працюють на згенерованому шарі.
        EffectRegistry registry = EffectRegistry.withDefaults();
        landscape.applyEffect(registry, InfraredEffect.CODE, Map.of());
        landscape.applyEffect(registry, BlurEffect.CODE, Map.of(BlurEffect.PARAM_RADIUS, 5));
        assertEquals(DemoProjectFactory.WIDTH, (int) landscape.getRendered().getImage().getWidth());
    }
}
