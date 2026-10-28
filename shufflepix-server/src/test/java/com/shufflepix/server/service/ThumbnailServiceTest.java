package com.shufflepix.server.service;

import com.shufflepix.common.image.PixelEffects;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThumbnailServiceTest {
    private final ThumbnailService service = new ThumbnailService(null, null, null, null);

    @Test
    void scalesLandscapePortraitSquareAndExtremeAspectRatiosWithinMaxSide() {
        BufferedImage fullHd = solidImage(1920, 1080, new Color(40, 120, 200, 255));
        BufferedImage scaledHd = service.scale(fullHd, 256);
        assertEquals(256, scaledHd.getWidth());
        assertEquals(144, scaledHd.getHeight());
        assertEquals(new Color(40, 120, 200, 255).getRGB(), scaledHd.getRGB(128, 72));

        BufferedImage portrait = solidImage(600, 1200, new Color(200, 50, 80, 255));
        BufferedImage scaledPortrait = service.scale(portrait, 256);
        assertEquals(128, scaledPortrait.getWidth());
        assertEquals(256, scaledPortrait.getHeight());

        BufferedImage square = solidImage(1024, 1024, new Color(10, 20, 30, 180));
        BufferedImage scaledSquare = service.scale(square, 256);
        assertEquals(256, scaledSquare.getWidth());
        assertEquals(256, scaledSquare.getHeight());

        // Extreme aspect ratio must stay >= 1 on the short side (CHECK width/height BETWEEN 1 AND 256).
        BufferedImage ultraWide = solidImage(4096, 1, Color.GREEN);
        BufferedImage scaledWide = service.scale(ultraWide, 256);
        assertEquals(256, scaledWide.getWidth());
        assertEquals(1, scaledWide.getHeight());
    }

    @Test
    void doesNotUpscaleImagesAlreadyWithinMaxSide() {
        BufferedImage small = solidImage(100, 60, new Color(12, 34, 56, 200));
        BufferedImage scaled = service.scale(small, 256);

        assertNotSame(small, scaled);
        assertEquals(100, scaled.getWidth());
        assertEquals(60, scaled.getHeight());
        assertEquals(small.getRGB(50, 30), scaled.getRGB(50, 30));
    }

    @Test
    void scaleValidatesArguments() {
        BufferedImage img = solidImage(10, 10, Color.WHITE);
        assertThrows(IllegalArgumentException.class, () -> service.scale(null, 256));
        assertThrows(IllegalArgumentException.class, () -> service.scale(img, 0));
        assertThrows(IllegalArgumentException.class, () -> service.scale(img, -10));
    }

    @Test
    void pixelEffectsApplyAllFiveEffectsAndIgnoreUnknownCode() {
        int[] pixels = {
                argb(255, 34, 139, 34), // forest green
                argb(255, 65, 130, 225), // blue sky
                0x00000000 // transparent
        };

        int[] gray = pixels.clone();
        assertTrue(PixelEffects.apply(gray, 3, 1, PixelEffects.CODE_GRAYSCALE, Map.of()));
        assertEquals(red(gray[0]), green(gray[0]));
        assertEquals(green(gray[0]), blue(gray[0]));
        assertEquals(0, gray[2]);

        int[] ir = pixels.clone();
        assertTrue(PixelEffects.apply(ir, 3, 1, PixelEffects.CODE_INFRARED, Map.of()));
        assertTrue(red(ir[0]) > red(gray[0]) + 60);
        assertTrue(red(ir[1]) < red(gray[1]) - 60);
        assertEquals(0, ir[2]);

        int[] neg = pixels.clone();
        assertTrue(PixelEffects.apply(neg, 3, 1, PixelEffects.CODE_NEGATIVE, Map.of()));
        assertEquals(argb(255, 221, 116, 221), neg[0]);
        assertEquals(0, neg[2]);

        int[] sepia = pixels.clone();
        assertTrue(PixelEffects.apply(sepia, 3, 1, PixelEffects.CODE_SEPIA, Map.of()));
        assertTrue(red(sepia[0]) >= green(sepia[0]) && green(sepia[0]) >= blue(sepia[0]));
        assertEquals(0, sepia[2]);

        // Straight ARGB blur around red block on transparent canvas must not create dark halo.
        int size = 31;
        int[] blur = new int[size * size];
        for (int y = 10; y <= 20; y++) {
            for (int x = 10; x <= 20; x++) {
                blur[y * size + x] = argb(255, 255, 0, 0);
            }
        }
        assertTrue(PixelEffects.apply(blur, size, size, PixelEffects.CODE_BLUR, Map.of(PixelEffects.PARAM_RADIUS, 4)));
        int fringe = blur[15 * size + 8];
        assertTrue(alpha(fringe) > 0 && alpha(fringe) < 255);
        assertTrue(red(fringe) >= 245, "Червоний канал на межі не має темніти, r=" + red(fringe));
        assertEquals(0, green(fringe));
        assertEquals(0, blue(fringe));

        int[] untouched = pixels.clone();
        assertFalse(PixelEffects.apply(untouched, 3, 1, "FUTURE_EFFECT", Map.of()));
        assertEquals(pixels[0], untouched[0]);
    }

    private static BufferedImage solidImage(int width, int height, Color color) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(color);
            g.fillRect(0, 0, width, height);
        } finally {
            g.dispose();
        }
        return img;
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
