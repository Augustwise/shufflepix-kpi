package com.shufflepix.client.core.style;

import java.util.Locale;

public record Color(int red, int green, int blue, int alpha) {
    public static final Color BLACK = new Color(0, 0, 0, 255);
    public static final Color WHITE = new Color(255, 255, 255, 255);
    public static final Color TRANSPARENT = new Color(0, 0, 0, 0);

    public Color {
        check(red);
        check(green);
        check(blue);
        check(alpha);
    }

    public static Color fromHex(String hex) {
        if (hex == null || !hex.matches("^#[0-9A-Fa-f]{8}$")) {
            throw new IllegalArgumentException("Очікується колір у форматі #RRGGBBAA: " + hex);
        }
        long v = Long.parseLong(hex.substring(1), 16);
        return new Color((int) (v >> 24) & 0xFF, (int) (v >> 16) & 0xFF, (int) (v >> 8) & 0xFF, (int) v & 0xFF);
    }

    public String toHex() {
        return String.format(Locale.ROOT, "#%02X%02X%02X%02X", red, green, blue, alpha);
    }

    public static Color fromFx(javafx.scene.paint.Color c) {
        return new Color((int) Math.round(c.getRed() * 255), (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255), (int) Math.round(c.getOpacity() * 255));
    }

    public javafx.scene.paint.Color toFx() {
        return javafx.scene.paint.Color.rgb(red, green, blue, alpha / 255.0);
    }

    public int toArgb() {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static void check(int component) {
        if (component < 0 || component > 255) {
            throw new IllegalArgumentException("Складова кольору має бути від 0 до 255: " + component);
        }
    }
}
