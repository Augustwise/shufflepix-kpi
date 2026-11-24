package com.shufflepix.client;

import com.shufflepix.client.core.Editor;
import com.shufflepix.client.core.image.RasterImage;
import com.shufflepix.client.core.model.Layer;
import com.shufflepix.client.core.model.Project;
import com.shufflepix.client.core.shape.LayerPixelRenderer;
import com.shufflepix.client.core.style.Color;
import com.shufflepix.client.core.style.Style;
import com.shufflepix.client.core.style.StyleFactory;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

/**
 * Створює готовий багатошаровий тестовий проєкт для швидкої ручної перевірки
 * інтерфейсу, інструментів, шарів та візуальних ефектів.
 */
public final class DemoProjectFactory {
    public static final int WIDTH = 800;
    public static final int HEIGHT = 600;

    private DemoProjectFactory() {
    }

    public static Project create() {
        StyleFactory styles = new StyleFactory();
        Project project = new Project(WIDTH, HEIGHT, Color.WHITE);
        project.setName("Тестовий проєкт");

        project.addLayer(0, createLandscapeLayer(styles));
        project.addLayer(1, createShapesLayer(styles));
        project.addLayer(2, createTextLayer(styles));
        project.addLayer(3, createHiddenLayer(styles));

        project.setActiveIndex(0);
        project.markClean();
        return project;
    }

    private static Layer createLandscapeLayer(StyleFactory styles) {
        WritableImage image = new WritableImage(WIDTH, HEIGHT);
        int[] pixels = new int[WIDTH * HEIGHT];

        int horizonY = 310;
        int shoreY = 445;

        for (int y = 0; y < HEIGHT; y++) {
            int row = y * WIDTH;
            int color;
            if (y < horizonY) {
                double t = (double) y / horizonY;
                color = lerpArgb(30, 85, 205, 120, 190, 248, t);
            } else if (y < shoreY) {
                double t = (double) (y - horizonY) / (shoreY - horizonY);
                color = lerpArgb(58, 168, 56, 32, 122, 40, t);
            } else {
                double t = (double) (y - shoreY) / (HEIGHT - shoreY);
                color = lerpArgb(32, 105, 195, 16, 52, 128, t);
            }
            for (int x = 0; x < WIDTH; x++) {
                pixels[row + x] = color;
            }
        }
        image.getPixelWriter().setPixels(0, 0, WIDTH, HEIGHT, PixelFormat.getIntArgbInstance(), pixels, 0, WIDTH);

        RasterImage raster = new RasterImage(image);
        LayerPixelRenderer r = new LayerPixelRenderer(raster);

        // Сонце
        r.drawEllipse(640, 45, 92, 92,
                styles.get(new Color(255, 225, 90, 120), new Color(255, 230, 110, 90), 2, Editor.DEFAULT_FONT, 16));
        r.drawEllipse(654, 59, 64, 64,
                styles.get(new Color(255, 205, 40, 255), new Color(255, 224, 70, 255), 2, Editor.DEFAULT_FONT, 16));

        // Хмари
        Style cloud = styles.get(new Color(255, 255, 255, 230), new Color(255, 255, 255, 230), 1, Editor.DEFAULT_FONT, 16);
        drawCloud(r, cloud, 110, 75, 1.0);
        drawCloud(r, cloud, 370, 55, 0.85);
        drawCloud(r, cloud, 520, 120, 0.7);

        // Зелені пагорби (наочні для інфрачервоного ефекту)
        r.drawEllipse(-80, 265, 420, 170,
                styles.get(new Color(40, 140, 45, 255), new Color(46, 155, 52, 255), 1, Editor.DEFAULT_FONT, 16));
        r.drawEllipse(260, 255, 460, 185,
                styles.get(new Color(34, 132, 42, 255), new Color(40, 146, 48, 255), 1, Editor.DEFAULT_FONT, 16));
        r.drawEllipse(540, 275, 360, 160,
                styles.get(new Color(48, 160, 54, 255), new Color(56, 172, 60, 255), 1, Editor.DEFAULT_FONT, 16));

        // Будиночок на лівому пагорбі
        r.drawRectangle(115, 295, 95, 70,
                styles.get(new Color(120, 90, 60, 255), new Color(242, 226, 198, 255), 2, Editor.DEFAULT_FONT, 16));
        r.drawRectangle(105, 270, 115, 28,
                styles.get(new Color(145, 28, 24, 255), new Color(205, 48, 42, 255), 2, Editor.DEFAULT_FONT, 16));
        r.drawRectangle(146, 315, 32, 28,
                styles.get(new Color(110, 75, 45, 255), new Color(255, 218, 95, 255), 2, Editor.DEFAULT_FONT, 16));

        // Дерева вздовж берега
        drawTree(r, styles, 295, 330, 1.0);
        drawTree(r, styles, 365, 345, 0.85);
        drawTree(r, styles, 565, 325, 1.15);
        drawTree(r, styles, 650, 340, 0.95);
        drawTree(r, styles, 725, 355, 0.8);

        // Відблиски на воді
        Style ripple = styles.get(new Color(175, 220, 255, 150), null, 3, Editor.DEFAULT_FONT, 16);
        r.drawLine(90, 480, 210, 480, ripple);
        r.drawLine(280, 510, 430, 510, ripple);
        r.drawLine(580, 475, 710, 475, ripple);
        r.drawLine(610, 520, 725, 520, ripple);
        r.drawLine(160, 555, 310, 555, ripple);

        return new Layer("Пейзаж", raster);
    }

    private static Layer createShapesLayer(StyleFactory styles) {
        RasterImage raster = RasterImage.filled(WIDTH, HEIGHT, Color.TRANSPARENT);
        LayerPixelRenderer r = new LayerPixelRenderer(raster);

        r.drawRectangle(45, 405, 130, 90,
                styles.get(new Color(150, 20, 20, 255), new Color(235, 64, 52, 220), 4, Editor.DEFAULT_FONT, 16));
        r.drawEllipse(215, 395, 125, 95,
                styles.get(new Color(20, 95, 115, 255), new Color(46, 196, 182, 215), 4, Editor.DEFAULT_FONT, 16));
        r.drawLine(375, 485, 510, 400,
                styles.get(new Color(255, 145, 0, 255), null, 6, Editor.DEFAULT_FONT, 16));
        r.drawPolyline(
                new double[]{380, 415, 445, 475, 510},
                new double[]{425, 400, 445, 415, 465},
                styles.get(new Color(155, 55, 210, 255), null, 5, Editor.DEFAULT_FONT, 16));

        Layer layer = new Layer("Фігури", raster);
        layer.setOpacity(0.85);
        return layer;
    }

    private static Layer createTextLayer(StyleFactory styles) {
        RasterImage raster = RasterImage.filled(WIDTH, HEIGHT, Color.TRANSPARENT);
        LayerPixelRenderer r = new LayerPixelRenderer(raster);

        r.drawText(34, 52, "ShufflePix Demo",
                styles.get(new Color(0, 0, 0, 110), null, 1, Editor.DEFAULT_FONT, 34));
        r.drawText(32, 50, "ShufflePix Demo",
                styles.get(new Color(255, 255, 255, 255), null, 1, Editor.DEFAULT_FONT, 34));
        r.drawText(32, 76, "Тестова сцена: шари, фігури, ефекти",
                styles.get(new Color(245, 248, 255, 235), null, 1, Editor.DEFAULT_FONT, 16));

        return new Layer("Підпис", raster);
    }

    private static Layer createHiddenLayer(StyleFactory styles) {
        RasterImage raster = RasterImage.filled(WIDTH, HEIGHT, Color.TRANSPARENT);
        LayerPixelRenderer r = new LayerPixelRenderer(raster);

        r.drawRectangle(540, 470, 220, 95,
                styles.get(new Color(180, 30, 120, 255), new Color(255, 110, 190, 210), 4, Editor.DEFAULT_FONT, 16));
        r.drawText(562, 525, "Прихований шар",
                styles.get(Color.WHITE, null, 1, Editor.DEFAULT_FONT, 22));

        Layer layer = new Layer("Прихований шар", raster);
        layer.setVisible(false);
        layer.setOpacity(0.65);
        return layer;
    }

    private static void drawCloud(LayerPixelRenderer r, Style style, double x, double y, double scale) {
        r.drawEllipse(x, y + 12 * scale, 56 * scale, 32 * scale, style);
        r.drawEllipse(x + 22 * scale, y, 64 * scale, 42 * scale, style);
        r.drawEllipse(x + 56 * scale, y + 10 * scale, 52 * scale, 32 * scale, style);
    }

    private static void drawTree(LayerPixelRenderer r, StyleFactory styles, double x, double y, double scale) {
        Style trunk = styles.get(new Color(85, 48, 24, 255), new Color(115, 68, 35, 255), 2, Editor.DEFAULT_FONT, 16);
        Style darkLeaf = styles.get(new Color(22, 105, 32, 255), new Color(28, 128, 38, 255), 1, Editor.DEFAULT_FONT, 16);
        Style brightLeaf = styles.get(new Color(45, 155, 50, 255), new Color(62, 180, 62, 255), 1, Editor.DEFAULT_FONT, 16);

        r.drawRectangle(x - 7 * scale, y + 25 * scale, 14 * scale, 48 * scale, trunk);
        r.drawEllipse(x - 38 * scale, y - 10 * scale, 76 * scale, 58 * scale, darkLeaf);
        r.drawEllipse(x - 28 * scale, y - 34 * scale, 58 * scale, 52 * scale, brightLeaf);
    }

    private static int lerpArgb(int r1, int g1, int b1, int r2, int g2, int b2, double t) {
        int r = (int) Math.round(r1 + (r2 - r1) * t);
        int g = (int) Math.round(g1 + (g2 - g1) * t);
        int b = (int) Math.round(b1 + (b2 - b1) * t);
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }
}
