package com.shufflepix.client.core;

import com.shufflepix.client.core.effect.BlurEffect;
import com.shufflepix.client.core.effect.Effect;
import com.shufflepix.client.core.effect.GrayscaleEffect;
import com.shufflepix.client.core.effect.InfraredEffect;
import com.shufflepix.client.core.effect.NegativeEffect;
import com.shufflepix.client.core.effect.SepiaEffect;
import com.shufflepix.client.core.history.ApplyEffectCommand;
import com.shufflepix.client.core.history.RemoveEffectCommand;
import com.shufflepix.client.core.image.RasterImage;
import com.shufflepix.client.core.model.Layer;
import com.shufflepix.client.core.model.Project;
import com.shufflepix.client.core.style.Color;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectTest {
    @Test
    void grayscaleConvertsRgbAndPreservesAlpha() {
        WritableImage raw = new WritableImage(5, 1);
        PixelWriter w = raw.getPixelWriter();
        w.setArgb(0, 0, argb(255, 255, 0, 0));
        w.setArgb(1, 0, argb(255, 0, 255, 0));
        w.setArgb(2, 0, argb(255, 120, 120, 120));
        w.setArgb(3, 0, argb(128, 100, 150, 200));
        w.setArgb(4, 0, 0x00000000);

        Image out = new GrayscaleEffect(new RasterImage(raw)).getImage();
        PixelReader r = out.getPixelReader();

        assertEquals(argb(255, 77, 77, 77), r.getArgb(0, 0));
        assertEquals(argb(255, 149, 149, 149), r.getArgb(1, 0));
        assertEquals(argb(255, 120, 120, 120), r.getArgb(2, 0));

        int semi = r.getArgb(3, 0);
        assertEquals(128, alpha(semi));
        assertEquals(red(semi), green(semi));
        assertEquals(green(semi), blue(semi));

        assertEquals(0x00000000, r.getArgb(4, 0));
    }

    @Test
    void infraredBrightensGreenFoliageAndDarkensBlueSkyAndWater() {
        WritableImage raw = new WritableImage(6, 1);
        PixelWriter w = raw.getPixelWriter();
        w.setArgb(0, 0, argb(255, 34, 139, 34));    // forest green foliage
        w.setArgb(1, 0, argb(255, 65, 130, 225));   // blue sky
        w.setArgb(2, 0, argb(255, 30, 90, 160));    // deep water
        w.setArgb(3, 0, argb(255, 128, 128, 128));  // neutral gray
        w.setArgb(4, 0, argb(180, 34, 139, 34));    // semi-transparent foliage
        w.setArgb(5, 0, 0x00000000);                // transparent

        RasterImage source = new RasterImage(raw);
        PixelReader gray = new GrayscaleEffect(source).getImage().getPixelReader();
        PixelReader ir = new InfraredEffect(source).getImage().getPixelReader();

        int foliageIr = red(ir.getArgb(0, 0));
        int foliageGray = red(gray.getArgb(0, 0));
        assertTrue(foliageIr > foliageGray + 60,
                "Зелена рослинність має бути значно світлішою: ir=" + foliageIr + ", gray=" + foliageGray);
        assertEquals(foliageIr, green(ir.getArgb(0, 0)));
        assertEquals(foliageIr, blue(ir.getArgb(0, 0)));

        int skyIr = red(ir.getArgb(1, 0));
        int skyGray = red(gray.getArgb(1, 0));
        assertTrue(skyIr < skyGray - 60,
                "Синє небо має бути значно темнішим: ir=" + skyIr + ", gray=" + skyGray);

        int waterIr = red(ir.getArgb(2, 0));
        assertTrue(waterIr < 20, "Вода має бути темною: ir=" + waterIr);

        assertEquals(argb(255, 128, 128, 128), ir.getArgb(3, 0));
        assertEquals(180, alpha(ir.getArgb(4, 0)));
        assertEquals(0x00000000, ir.getArgb(5, 0));
    }

    @Test
    void negativeInvertsColorsAndIsSelfInverse() {
        WritableImage raw = new WritableImage(3, 1);
        PixelWriter w = raw.getPixelWriter();
        w.setArgb(0, 0, argb(255, 10, 120, 240));
        w.setArgb(1, 0, argb(90, 200, 50, 0));
        w.setArgb(2, 0, 0x00000000);

        NegativeEffect once = new NegativeEffect(new RasterImage(raw));
        PixelReader r1 = once.getImage().getPixelReader();
        assertEquals(argb(255, 245, 135, 15), r1.getArgb(0, 0));
        int semi1 = r1.getArgb(1, 0);
        assertEquals(90, alpha(semi1));
        assertTrue(Math.abs(55 - red(semi1)) <= 2);
        assertTrue(Math.abs(205 - green(semi1)) <= 2);
        assertEquals(255, blue(semi1));
        assertEquals(0x00000000, r1.getArgb(2, 0));

        PixelReader r2 = new NegativeEffect(once).getImage().getPixelReader();
        assertEquals(argb(255, 10, 120, 240), r2.getArgb(0, 0));
        int semi2 = r2.getArgb(1, 0);
        assertEquals(90, alpha(semi2));
        assertTrue(Math.abs(200 - red(semi2)) <= 3);
        assertTrue(Math.abs(50 - green(semi2)) <= 3);
        assertEquals(0, blue(semi2));
        assertEquals(0x00000000, r2.getArgb(2, 0));
    }

    @Test
    void sepiaProducesWarmToneAndPreservesAlpha() {
        WritableImage raw = new WritableImage(3, 1);
        PixelWriter w = raw.getPixelWriter();
        w.setArgb(0, 0, argb(255, 128, 128, 128));
        w.setArgb(1, 0, argb(255, 0, 0, 0));
        w.setArgb(2, 0, argb(100, 128, 128, 128));

        PixelReader r = new SepiaEffect(new RasterImage(raw)).getImage().getPixelReader();
        int mid = r.getArgb(0, 0);
        assertTrue(red(mid) > green(mid) && green(mid) > blue(mid),
                "Сепія має давати теплий коричневий відтінок (R > G > B)");
        assertEquals(argb(255, 0, 0, 0), r.getArgb(1, 0));
        assertEquals(100, alpha(r.getArgb(2, 0)));
    }

    @Test
    void blurPreservesUniformImageAndAvoidsDarkHaloOnTransparency() {
        RasterImage solid = RasterImage.filled(40, 30, new Color(100, 150, 200, 255));
        Image blurredSolid = new BlurEffect(solid, 8).getImage();
        PixelReader solidReader = blurredSolid.getPixelReader();
        assertEquals(new Color(100, 150, 200, 255).toArgb(), solidReader.getArgb(0, 0));
        assertEquals(new Color(100, 150, 200, 255).toArgb(), solidReader.getArgb(20, 15));
        assertEquals(new Color(100, 150, 200, 255).toArgb(), solidReader.getArgb(39, 29));

        // Red block in center of transparent layer: blurred fringe must remain pure red, not dark gray.
        WritableImage raw = new WritableImage(41, 41);
        PixelWriter writer = raw.getPixelWriter();
        for (int y = 15; y <= 25; y++) {
            for (int x = 15; x <= 25; x++) {
                writer.setArgb(x, y, argb(255, 255, 0, 0));
            }
        }
        Image blurred = new BlurEffect(new RasterImage(raw), 5).getImage();
        PixelReader reader = blurred.getPixelReader();
        int fringe = reader.getArgb(13, 20);
        assertTrue(alpha(fringe) > 0 && alpha(fringe) < 255, "Очікувалась напівпрозора межа, alpha=" + alpha(fringe));
        assertTrue(red(fringe) >= 245, "Червоний канал на межі не має темніти, r=" + red(fringe));
        assertEquals(0, green(fringe));
        assertEquals(0, blue(fringe));

        // Larger radius spreads peak energy more than smaller radius.
        Image smallBlur = new BlurEffect(new RasterImage(raw), 1).getImage();
        Image largeBlur = new BlurEffect(new RasterImage(raw), 10).getImage();
        assertTrue(alpha(smallBlur.getPixelReader().getArgb(20, 20))
                > alpha(largeBlur.getPixelReader().getArgb(20, 20)));
    }

    @Test
    void blurHandlesTinyImagesAndValidatesRadius() {
        RasterImage oneByOne = RasterImage.filled(1, 1, new Color(42, 84, 126, 255));
        Image blurred = new BlurEffect(oneByOne, 15).getImage();
        assertEquals(new Color(42, 84, 126, 255).toArgb(), blurred.getPixelReader().getArgb(0, 0));

        assertThrows(IllegalArgumentException.class, () -> new BlurEffect(oneByOne, 0));
        assertThrows(IllegalArgumentException.class, () -> new BlurEffect(oneByOne, -3));
    }

    @Test
    void chainingEffectsIsNonDestructiveAndSupportsUndoRedo() {
        Editor editor = new Editor();
        Project project = Project.blank(16, 16, new Color(34, 139, 34, 255));
        editor.setProject(project);
        Layer layer = project.getActiveLayer();

        int originalArgb = layer.getContent().getImage().getPixelReader().getArgb(8, 8);

        editor.execute(new ApplyEffectCommand(project, layer, editor.getEffects(), GrayscaleEffect.CODE, Map.of()));
        int afterGray = layer.getRendered().getImage().getPixelReader().getArgb(8, 8);

        editor.execute(new ApplyEffectCommand(project, layer, editor.getEffects(), NegativeEffect.CODE, Map.of()));
        int afterGrayAndNeg = layer.getRendered().getImage().getPixelReader().getArgb(8, 8);

        // Source pixels must stay untouched (Decorator pattern).
        assertEquals(originalArgb, layer.getContent().getImage().getPixelReader().getArgb(8, 8));
        assertEquals(255 - red(afterGray), red(afterGrayAndNeg));

        // Remove the first effect (Grayscale); only Negative remains.
        editor.execute(new RemoveEffectCommand(project, layer, 0));
        int onlyNeg = layer.getRendered().getImage().getPixelReader().getArgb(8, 8);
        assertEquals(argb(255, 255 - 34, 255 - 139, 255 - 34), onlyNeg);

        // Undo removal -> Grayscale + Negative restored.
        editor.undo();
        assertEquals(afterGrayAndNeg, layer.getRendered().getImage().getPixelReader().getArgb(8, 8));

        // Undo Negative -> only Grayscale.
        editor.undo();
        assertEquals(afterGray, layer.getRendered().getImage().getPixelReader().getArgb(8, 8));

        // Undo Grayscale -> original pixels.
        editor.undo();
        assertEquals(originalArgb, layer.getRendered().getImage().getPixelReader().getArgb(8, 8));
    }

    @Test
    void cachesOutputAndUpdatesDisplayNames() {
        RasterImage source = RasterImage.filled(16, 16, new Color(50, 100, 150, 255));
        Effect effect = new SepiaEffect(source);

        Image first = effect.getImage();
        Image second = effect.getImage();
        assertSame(first, second, "Повторний виклик getImage() має повертати кешований результат");

        Effect rewrapped = effect.wrap(RasterImage.filled(16, 16, new Color(10, 20, 30, 255)));
        assertNotSame(first, rewrapped.getImage());

        assertEquals("Чорно-білий", new GrayscaleEffect(source).getDisplayName());
        assertEquals("Інфрачервоний", new InfraredEffect(source).getDisplayName());
        assertEquals("Негатив", new NegativeEffect(source).getDisplayName());
        assertEquals("Сепія", new SepiaEffect(source).getDisplayName());
        assertEquals("Розмиття (радіус 12)", new BlurEffect(source, 12).getDisplayName());
    }

    @Test
    void meetsNfr01PerformanceOnFullHd() {
        int width = 1920;
        int height = 1080;
        WritableImage fullHd = new WritableImage(width, height);
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                pixels[row + x] = argb(255, (x * 255) / width, (y * 255) / height, ((x + y) & 0xFF));
            }
        }
        fullHd.getPixelWriter().setPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
        RasterImage source = new RasterImage(fullHd);

        List<Effect> effects = List.of(
                new GrayscaleEffect(source),
                new InfraredEffect(source),
                new NegativeEffect(source),
                new SepiaEffect(source),
                new BlurEffect(source, BlurEffect.DEFAULT_RADIUS),
                new BlurEffect(source, 50)
        );

        for (Effect effect : effects) {
            long start = System.nanoTime();
            Image result = effect.getImage();
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            assertEquals(width, (int) result.getWidth());
            assertEquals(height, (int) result.getHeight());
            assertTrue(elapsedMs <= 1000,
                    effect.getDisplayName() + " перевищив ліміт NFR-01 (1000 мс): " + elapsedMs + " мс");
        }
    }

    private static int argb(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
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
