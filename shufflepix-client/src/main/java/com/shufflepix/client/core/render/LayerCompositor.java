package com.shufflepix.client.core.render;

import com.shufflepix.client.core.model.Layer;
import com.shufflepix.client.core.model.Project;
import com.shufflepix.client.core.style.Color;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;

import java.util.Arrays;
import java.util.Objects;

public class LayerCompositor {
    // Process layers in horizontal row chunks to keep temporary buffer memory small
    private static final int CHUNK_ROWS = 64;
    private static final double INV_255 = 1.0 / 255.0;

    public WritableImage compose(Project project, Color background) {
        Objects.requireNonNull(project, "Проєкт не може бути null");
        int width = project.getWidth();
        int height = project.getHeight();
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Некоректні розміри проєкту: " + width + "x" + height);
        }

        int[] resultPixels = new int[width * height];
        // Pre-fill the canvas when a visible background color is provided
        if (background != null && background.alpha() > 0) {
            Arrays.fill(resultPixels, background.toArgb());
        }

        int[] chunkBuffer = null;
        for (Layer layer : project.getLayers()) {
            // Skip hidden layers
            if (!layer.isVisible()) {
                continue;
            }
            // Skip fully transparent layers
            double opacity = clampOpacity(layer.getOpacity());
            if (opacity <= 0.0) {
                continue;
            }
            // Skip layers that have no rendered image yet
            Image rendered = layer.getRendered().getImage();
            if (rendered == null) {
                continue;
            }
            PixelReader reader = rendered.getPixelReader();
            if (reader == null) {
                throw new IllegalStateException("Не вдалося прочитати пікселі шару «" + layer.getName() + "»");
            }
            // Clamp copy region to the intersection of the canvas and layer dimensions
            int copyW = Math.min(width, (int) rendered.getWidth());
            int copyH = Math.min(height, (int) rendered.getHeight());
            if (copyW <= 0 || copyH <= 0) {
                continue;
            }

            // Reuse or grow the chunk buffer to fit this layer's chunk size
            int maxChunkRows = Math.min(CHUNK_ROWS, copyH);
            int requiredSize = copyW * maxChunkRows;
            if (chunkBuffer == null || chunkBuffer.length < requiredSize) {
                chunkBuffer = new int[requiredSize];
            }

            blendLayer(resultPixels, width, reader, copyW, copyH, opacity, chunkBuffer);
        }

        // Copy the final ARGB pixel buffer into a JavaFX WritableImage
        WritableImage result = new WritableImage(width, height);
        result.getPixelWriter().setPixels(0, 0, width, height,
                PixelFormat.getIntArgbInstance(), resultPixels, 0, width);
        return result;
    }

    public boolean hasVisibleLayers(Project project) {
        Objects.requireNonNull(project, "Проєкт не може бути null");
        return project.getLayers().stream().anyMatch(Layer::isVisible);
    }

    private static void blendLayer(int[] dstPixels, int dstWidth, PixelReader reader,
                                   int copyW, int copyH, double opacity, int[] chunkBuffer) {
        boolean opaqueLayer = opacity >= 1.0;
        for (int yStart = 0; yStart < copyH; yStart += CHUNK_ROWS) {
            // Read the next horizontal strip of layer pixels into the chunk buffer
            int rows = Math.min(CHUNK_ROWS, copyH - yStart);
            reader.getPixels(0, yStart, copyW, rows,
                    PixelFormat.getIntArgbInstance(), chunkBuffer, 0, copyW);

            for (int r = 0; r < rows; r++) {
                int srcRowOffset = r * copyW;
                int dstRowOffset = (yStart + r) * dstWidth;
                for (int x = 0; x < copyW; x++) {
                    int srcArgb = chunkBuffer[srcRowOffset + x];
                    int srcA = (srcArgb >>> 24) & 0xFF;
                    // Skip fully transparent source pixels
                    if (srcA == 0) {
                        continue;
                    }
                    int dstIdx = dstRowOffset + x;
                    // Fast path: fully opaque pixel on a 100% opaque layer overwrites destination directly
                    if (srcA == 255 && opaqueLayer) {
                        dstPixels[dstIdx] = srcArgb;
                        continue;
                    }
                    dstPixels[dstIdx] = blendPixel(srcArgb, srcA, dstPixels[dstIdx], opacity);
                }
            }
        }
    }

    private static int blendPixel(int srcArgb, int srcA, int dstArgb, double opacity) {
        // Effective source alpha in [0, 1] after applying layer opacity
        double sa = (srcA * INV_255) * opacity;
        int dstA = (dstArgb >>> 24) & 0xFF;

        // Fast path: empty destination pixel takes source RGB with scaled alpha
        if (dstA == 0) {
            int outA = clampByte((int) Math.round(sa * 255.0));
            return outA == 0 ? 0 : (outA << 24) | (srcArgb & 0x00FFFFFF);
        }

        int sr = (srcArgb >>> 16) & 0xFF;
        int sg = (srcArgb >>> 8) & 0xFF;
        int sb = srcArgb & 0xFF;

        int dr = (dstArgb >>> 16) & 0xFF;
        int dg = (dstArgb >>> 8) & 0xFF;
        int db = dstArgb & 0xFF;

        double invSa = 1.0 - sa;
        // Fast path: opaque destination stays opaque, so simple linear interpolation is enough
        if (dstA == 255) {
            int or = clampByte((int) Math.round(sr * sa + dr * invSa));
            int og = clampByte((int) Math.round(sg * sa + dg * invSa));
            int ob = clampByte((int) Math.round(sb * sa + db * invSa));
            return (0xFF << 24) | (or << 16) | (og << 8) | ob;
        }

        // General case: Porter-Duff "source-over" blending for two semi-transparent pixels
        double da = dstA * INV_255;
        double dstWeight = da * invSa;
        double outAlpha = sa + dstWeight;
        // Guard against division by zero if combined alpha rounds down to zero
        if (outAlpha <= 0.0) {
            return 0;
        }
        double invOutAlpha = 1.0 / outAlpha;
        int or = clampByte((int) Math.round((sr * sa + dr * dstWeight) * invOutAlpha));
        int og = clampByte((int) Math.round((sg * sa + dg * dstWeight) * invOutAlpha));
        int ob = clampByte((int) Math.round((sb * sa + db * dstWeight) * invOutAlpha));
        int oa = clampByte((int) Math.round(outAlpha * 255.0));
        return (oa << 24) | (or << 16) | (og << 8) | ob;
    }

    private static double clampOpacity(double opacity) {
        if (opacity <= 0.0) {
            return 0.0;
        }
        return Math.min(1.0, opacity);
    }

    private static int clampByte(int value) {
        return value < 0 ? 0 : Math.min(255, value);
    }
}
