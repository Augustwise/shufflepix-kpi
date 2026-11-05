package com.shufflepix.client.core;

import com.shufflepix.client.core.effect.BlurEffect;
import com.shufflepix.client.core.effect.EffectRegistry;
import com.shufflepix.client.core.effect.GrayscaleEffect;
import com.shufflepix.client.core.effect.NegativeEffect;
import com.shufflepix.client.core.image.LayerImage;
import com.shufflepix.client.core.image.RasterImage;
import com.shufflepix.client.core.model.Layer;
import com.shufflepix.client.core.model.Project;
import com.shufflepix.client.core.render.LayerCompositor;
import com.shufflepix.client.core.style.Color;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayerCompositorTest {
    private static final Color RED = new Color(255, 0, 0, 255);
    private static final Color GREEN = new Color(0, 255, 0, 255);
    private static final Color BLUE = new Color(0, 0, 255, 255);

    private final LayerCompositor compositor = new LayerCompositor();

    @Test
    void composesVisibleLayersBottomToTop() {
        Project project = new Project(10, 10, Color.WHITE);
        project.addLayer(0, new Layer("Bottom", RasterImage.filled(10, 10, BLUE)));

        WritableImage topPixels = new WritableImage(10, 10);
        PixelWriter writer = topPixels.getPixelWriter();
        for (int y = 2; y < 8; y++) {
            for (int x = 2; x < 8; x++) {
                writer.setArgb(x, y, RED.toArgb());
            }
        }
        project.addLayer(1, new Layer("Top", new RasterImage(topPixels)));

        WritableImage result = compositor.compose(project, Color.WHITE);
        PixelReader reader = result.getPixelReader();

        assertEquals(BLUE.toArgb(), reader.getArgb(0, 0));
        assertEquals(RED.toArgb(), reader.getArgb(5, 5));

        // Swapping order puts Bottom on top of Top.
        project.moveLayer(0, 1);
        WritableImage swapped = compositor.compose(project, Color.WHITE);
        assertEquals(BLUE.toArgb(), swapped.getPixelReader().getArgb(5, 5));
    }

    @Test
    void skipsHiddenLayersWithoutLoadingLazyContent() {
        Project project = new Project(8, 8, Color.WHITE);
        project.addLayer(0, new Layer("Visible", RasterImage.filled(8, 8, GREEN)));

        AtomicBoolean loaded = new AtomicBoolean(false);
        LayerImage lazyImage = new LayerImage() {
            @Override
            public Image getImage() {
                loaded.set(true);
                return RasterImage.filled(8, 8, RED).getImage();
            }

            @Override
            public int getWidth() {
                return 8;
            }

            @Override
            public int getHeight() {
                return 8;
            }

            @Override
            public LayerImage copy() {
                return this;
            }
        };
        Layer hidden = new Layer("Hidden", lazyImage);
        hidden.setVisible(false);
        project.addLayer(1, hidden);

        WritableImage result = compositor.compose(project, Color.TRANSPARENT);

        assertFalse(loaded.get(), "Прихований шар не повинен завантажувати растрові дані");
        assertEquals(GREEN.toArgb(), result.getPixelReader().getArgb(4, 4));
    }

    @Test
    void blendsLayerOpacityAndPerPixelAlphaUsingSrcOver() {
        // 1) Opaque red (50% layer opacity) over opaque blue background -> (128, 0, 128, 255)
        Project overOpaque = new Project(4, 4, BLUE);
        Layer halfRed = new Layer("HalfRed", RasterImage.filled(4, 4, RED));
        halfRed.setOpacity(0.5);
        overOpaque.addLayer(0, halfRed);

        int blended = compositor.compose(overOpaque, BLUE).getPixelReader().getArgb(2, 2);
        assertEquals(255, alpha(blended));
        assertNear(128, red(blended), 1);
        assertEquals(0, green(blended));
        assertNear(128, blue(blended), 1);

        // 2) Opaque red (50% layer opacity) over transparent background -> pure red at ~128 alpha
        int overTransparent = compositor.compose(overOpaque, Color.TRANSPARENT).getPixelReader().getArgb(2, 2);
        assertNear(128, alpha(overTransparent), 1);
        assertNear(255, red(overTransparent), 1);
        assertEquals(0, green(overTransparent));
        assertEquals(0, blue(overTransparent));

        // 3) Semi-transparent red (alpha 128, opacity 1.0) over semi-transparent blue (alpha 128)
        Project semiOverSemi = new Project(4, 4, Color.TRANSPARENT);
        semiOverSemi.addLayer(0, new Layer("SemiBlue", RasterImage.filled(4, 4, new Color(0, 0, 255, 128))));
        semiOverSemi.addLayer(1, new Layer("SemiRed", RasterImage.filled(4, 4, new Color(255, 0, 0, 128))));

        int semiResult = compositor.compose(semiOverSemi, Color.TRANSPARENT).getPixelReader().getArgb(1, 1);
        // Porter-Duff SrcOver: a_out = 0.502 + 0.502*(1-0.502) = 0.752 (192), R = 170, B = 85
        assertNear(192, alpha(semiResult), 2);
        assertNear(170, red(semiResult), 3);
        assertEquals(0, green(semiResult));
        assertNear(85, blue(semiResult), 3);

        // 4) Zero opacity layer contributes nothing
        Project zeroOpacity = new Project(4, 4, Color.WHITE);
        zeroOpacity.addLayer(0, new Layer("Base", RasterImage.filled(4, 4, GREEN)));
        Layer invisibleByOpacity = new Layer("Zero", RasterImage.filled(4, 4, RED));
        invisibleByOpacity.setOpacity(0.0);
        zeroOpacity.addLayer(1, invisibleByOpacity);

        assertEquals(GREEN.toArgb(), compositor.compose(zeroOpacity, Color.WHITE).getPixelReader().getArgb(0, 0));
    }

    @Test
    void appliesLayerEffectChainInOrderWithoutMutatingSource() {
        EffectRegistry registry = EffectRegistry.withDefaults();
        Color original = new Color(34, 139, 34, 255);
        Project project = Project.blank(12, 12, original);
        Layer layer = project.getActiveLayer();

        layer.applyEffect(registry, GrayscaleEffect.CODE, Map.of());
        layer.applyEffect(registry, NegativeEffect.CODE, Map.of());

        WritableImage composed = compositor.compose(project, Color.TRANSPARENT);
        int expected = layer.getRendered().getImage().getPixelReader().getArgb(6, 6);
        assertEquals(expected, composed.getPixelReader().getArgb(6, 6));

        // Source pixels and effects remain intact (Table 1.7 postcondition).
        assertEquals(original.toArgb(), layer.getContent().getImage().getPixelReader().getArgb(6, 6));
        assertEquals(2, layer.getEffects().size());

        // Mutating the composed output does not affect the layer.
        composed.getPixelWriter().setArgb(6, 6, RED.toArgb());
        assertEquals(original.toArgb(), layer.getContent().getImage().getPixelReader().getArgb(6, 6));
    }

    @Test
    void handlesAllHiddenLayersAndBackgroundVariants() {
        Project project = Project.blank(8, 8, RED);
        project.getActiveLayer().setVisible(false);

        assertFalse(compositor.hasVisibleLayers(project));

        Color customBg = new Color(40, 80, 120, 255);
        assertEquals(customBg.toArgb(), compositor.compose(project, customBg).getPixelReader().getArgb(3, 3));
        assertEquals(0x00000000, compositor.compose(project, Color.TRANSPARENT).getPixelReader().getArgb(3, 3));
        assertEquals(0x00000000, compositor.compose(project, null).getPixelReader().getArgb(3, 3));

        project.getActiveLayer().setVisible(true);
        assertTrue(compositor.hasVisibleLayers(project));
    }

    @Test
    void composesFullHdMultiLayerProjectQuickly() {
        int width = 1920;
        int height = 1080;
        Project project = Project.blank(width, height, Color.WHITE);
        Layer overlay = new Layer("Overlay", RasterImage.filled(width, height, new Color(200, 100, 50, 128)));
        overlay.setOpacity(0.5);
        project.addLayer(1, overlay);
        Layer blurred = Layer.transparent("Top", width, height);
        blurred.applyEffect(EffectRegistry.withDefaults(), BlurEffect.CODE, Map.of(BlurEffect.PARAM_RADIUS, 3));
        project.addLayer(2, blurred);

        long start = System.nanoTime();
        WritableImage composed = compositor.compose(project, Color.WHITE);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        assertEquals(width, (int) composed.getWidth());
        assertEquals(height, (int) composed.getHeight());
        assertTrue(elapsedMs <= 1000, "Композиція Full HD занадто повільна: " + elapsedMs + " мс");
    }

    @Test
    void rejectsNullProject() {
        assertThrows(NullPointerException.class, () -> compositor.compose(null, Color.WHITE));
        assertThrows(NullPointerException.class, () -> compositor.hasVisibleLayers(null));
    }

    private static void assertNear(int expected, int actual, int tolerance) {
        assertTrue(Math.abs(expected - actual) <= tolerance,
                "Очікувалося " + expected + " (±" + tolerance + "), отримано " + actual);
    }

    private static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    private static int red(int argb) {
        return (argb >>> 16) & 0xFF;
    }

    private static int green(int argb) {
        return (argb >>> 8) & 0xFF;
    }

    private static int blue(int argb) {
        return argb & 0xFF;
    }
}
