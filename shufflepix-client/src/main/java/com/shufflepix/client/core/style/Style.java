package com.shufflepix.client.core.style;

import java.util.Objects;

public final class Style {
    private final Color strokeColor;
    private final Color fillColor;
    private final double lineWidth;
    private final String fontFamily;
    private final double fontSize;

    Style(Color strokeColor, Color fillColor, double lineWidth, String fontFamily, double fontSize) {
        this.strokeColor = Objects.requireNonNull(strokeColor);
        this.fillColor = fillColor;
        this.lineWidth = lineWidth;
        this.fontFamily = Objects.requireNonNull(fontFamily);
        this.fontSize = fontSize;
    }

    public Color getStrokeColor() {
        return strokeColor;
    }

    public Color getFillColor() {
        return fillColor;
    }

    public boolean isFilled() {
        return fillColor != null;
    }

    public double getLineWidth() {
        return lineWidth;
    }

    public String getFontFamily() {
        return fontFamily;
    }

    public double getFontSize() {
        return fontSize;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Style s
                && strokeColor.equals(s.strokeColor)
                && Objects.equals(fillColor, s.fillColor)
                && Double.compare(lineWidth, s.lineWidth) == 0
                && fontFamily.equals(s.fontFamily)
                && Double.compare(fontSize, s.fontSize) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(strokeColor, fillColor, lineWidth, fontFamily, fontSize);
    }
}
