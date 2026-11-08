package com.shufflepix.client.core.shape;

import com.shufflepix.client.core.image.RasterImage;
import com.shufflepix.client.core.style.Color;
import com.shufflepix.client.core.style.Style;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.function.Consumer;

public class LayerPixelRenderer implements ShapeRenderer {
    private final RasterImage target;

    public LayerPixelRenderer(RasterImage target) {
        this.target = target;
    }

    @Override
    public void drawLine(double x1, double y1, double x2, double y2, Style style) {
        render(g -> {
            applyStroke(g, style, BasicStroke.JOIN_ROUND);
            g.draw(new Line2D.Double(x1, y1, x2, y2));
        });
    }

    @Override
    public void drawRectangle(double x, double y, double width, double height, Style style) {
        double rx = width < 0 ? x + width : x;
        double ry = height < 0 ? y + height : y;
        double rw = Math.abs(width);
        double rh = Math.abs(height);
        if (rw == 0 && rh == 0) {
            return;
        }
        render(g -> {
            Rectangle2D.Double rect = new Rectangle2D.Double(rx, ry, rw, rh);
            if (style.isFilled() && rw > 0 && rh > 0) {
                g.setColor(toAwtColor(style.getFillColor()));
                g.fill(rect);
            }
            applyStroke(g, style, BasicStroke.JOIN_MITER);
            g.draw(rect);
        });
    }

    @Override
    public void drawEllipse(double x, double y, double width, double height, Style style) {
        double ex = width < 0 ? x + width : x;
        double ey = height < 0 ? y + height : y;
        double ew = Math.abs(width);
        double eh = Math.abs(height);
        if (ew == 0 || eh == 0) {
            return;
        }
        render(g -> {
            Ellipse2D.Double ellipse = new Ellipse2D.Double(ex, ey, ew, eh);
            if (style.isFilled()) {
                g.setColor(toAwtColor(style.getFillColor()));
                g.fill(ellipse);
            }
            applyStroke(g, style, BasicStroke.JOIN_ROUND);
            g.draw(ellipse);
        });
    }

    @Override
    public void drawPolyline(double[] xs, double[] ys, Style style) {
        int n = Math.min(xs.length, ys.length);
        if (n == 0) {
            return;
        }
        render(g -> {
            applyStroke(g, style, BasicStroke.JOIN_ROUND);
            g.draw(toPolylinePath(xs, ys, n));
        });
    }

    @Override
    public void drawText(double x, double y, String text, Style style) {
        if (text == null || text.isEmpty()) {
            return;
        }
        render(g -> {
            Font font = new Font(resolveFontFamily(style.getFontFamily()), Font.PLAIN, 1)
                    .deriveFont((float) style.getFontSize());
            g.setFont(font);
            g.setColor(toAwtColor(style.getStrokeColor()));
            FontMetrics metrics = g.getFontMetrics(font);
            float lineY = (float) y;
            for (String line : text.split("\\R", -1)) {
                g.drawString(line, (float) x, lineY);
                lineY += metrics.getHeight();
            }
        });
    }

    @Override
    public void erasePolyline(double[] xs, double[] ys, double width) {
        int n = Math.min(xs.length, ys.length);
        if (n == 0 || width <= 0) {
            return;
        }
        render(g -> {
            g.setComposite(AlphaComposite.DstOut);
            g.setColor(java.awt.Color.BLACK);
            g.setStroke(new BasicStroke((float) width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(toPolylinePath(xs, ys, n));
        });
    }

    /**
     * Bridges the target JavaFX {@link WritableImage} with an AWT {@link Graphics2D} context:
     * copies the layer's current pixels into an ARGB {@link BufferedImage}, configures
     * anti-aliased rendering hints, runs {@code action}, and writes the updated pixels back.
     */
    private void render(Consumer<Graphics2D> action) {
        int width = target.getWidth();
        int height = target.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        WritableImage pixels = target.getPixels();
        BufferedImage buffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] data = ((DataBufferInt) buffer.getRaster().getDataBuffer()).getData();
        pixels.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), data, 0, width);

        Graphics2D g = buffer.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            action.accept(g);
        } finally {
            g.dispose();
        }

        pixels.getPixelWriter().setPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), data, 0, width);
    }

    private static void applyStroke(Graphics2D g, Style style, int join) {
        g.setColor(toAwtColor(style.getStrokeColor()));
        float width = (float) Math.max(0.0, style.getLineWidth());
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, join));
    }

    /**
     * Connects the given (x, y) coordinates into one continuous line path.
     * If there is only one point, draws to itself so a single click still renders as a dot.
     */
    private static Path2D.Double toPolylinePath(double[] xs, double[] ys, int count) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(xs[0], ys[0]);
        if (count == 1) {
            path.lineTo(xs[0], ys[0]);
        } else {
            for (int i = 1; i < count; i++) {
                path.lineTo(xs[i], ys[i]);
            }
        }
        return path;
    }

    private static java.awt.Color toAwtColor(Color c) {
        return new java.awt.Color(c.red(), c.green(), c.blue(), c.alpha());
    }

    private static String resolveFontFamily(String family) {
        if ("System".equalsIgnoreCase(family)) {
            try {
                return javafx.scene.text.Font.getDefault().getFamily();
            } catch (RuntimeException ignored) {
                return Font.SANS_SERIF;
            }
        }
        return family;
    }
}
