package com.shufflepix.common.image;

import java.util.Map;

/**
 * Pure ARGB pixel implementations of visual effects shared between client decorators
 * and server-side thumbnail generation.
 */
public final class PixelEffects {
    public static final String CODE_GRAYSCALE = "GRAYSCALE";
    public static final String CODE_INFRARED = "INFRARED";
    public static final String CODE_NEGATIVE = "NEGATIVE";
    public static final String CODE_SEPIA = "SEPIA";
    public static final String CODE_BLUR = "BLUR";

    public static final String PARAM_RADIUS = "radius";
    public static final int DEFAULT_BLUR_RADIUS = 5;
    private static final int BOX_PASSES = 3;

    private PixelEffects() {
    }

    public static int grayscalePixel(int argb) {
        int a = argb & 0xFF000000;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        int gray = (77 * r + 150 * g + 29 * b + 128) >>> 8;
        return a | (gray << 16) | (gray << 8) | gray;
    }

    public static int infraredPixel(int argb) {
        int a = argb & 0xFF000000;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        int ir = clamp((int) Math.round(0.50 * r + 1.40 * g - 0.90 * b));
        return a | (ir << 16) | (ir << 8) | ir;
    }

    public static int negativePixel(int argb) {
        int a = argb & 0xFF000000;
        int r = 255 - ((argb >>> 16) & 0xFF);
        int g = 255 - ((argb >>> 8) & 0xFF);
        int b = 255 - (argb & 0xFF);
        return a | (r << 16) | (g << 8) | b;
    }

    public static int sepiaPixel(int argb) {
        int a = argb & 0xFF000000;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        int nr = clamp((int) Math.round(0.393 * r + 0.769 * g + 0.189 * b));
        int ng = clamp((int) Math.round(0.349 * r + 0.686 * g + 0.168 * b));
        int nb = clamp((int) Math.round(0.272 * r + 0.534 * g + 0.131 * b));
        return a | (nr << 16) | (ng << 8) | nb;
    }

    /**
     * Applies a known visual effect in-place on straight (non-premultiplied) ARGB pixels.
     *
     * @return {@code true} if the effect code was recognized and applied, {@code false} otherwise
     */
    public static boolean apply(int[] argb, int width, int height, String effectCode, Map<String, Object> parameters) {
        if (argb == null || width <= 0 || height <= 0 || effectCode == null) {
            return false;
        }
        return switch (effectCode) {
            case CODE_GRAYSCALE -> {
                for (int i = 0; i < argb.length; i++) {
                    if ((argb[i] >>> 24) != 0) {
                        argb[i] = grayscalePixel(argb[i]);
                    }
                }
                yield true;
            }
            case CODE_INFRARED -> {
                for (int i = 0; i < argb.length; i++) {
                    if ((argb[i] >>> 24) != 0) {
                        argb[i] = infraredPixel(argb[i]);
                    }
                }
                yield true;
            }
            case CODE_NEGATIVE -> {
                for (int i = 0; i < argb.length; i++) {
                    if ((argb[i] >>> 24) != 0) {
                        argb[i] = negativePixel(argb[i]);
                    }
                }
                yield true;
            }
            case CODE_SEPIA -> {
                for (int i = 0; i < argb.length; i++) {
                    if ((argb[i] >>> 24) != 0) {
                        argb[i] = sepiaPixel(argb[i]);
                    }
                }
                yield true;
            }
            case CODE_BLUR -> {
                int radius = extractBlurRadius(parameters);
                blurArgb(argb, width, height, radius);
                yield true;
            }
            default -> false;
        };
    }

    public static int extractBlurRadius(Map<String, Object> parameters) {
        if (parameters == null) {
            return DEFAULT_BLUR_RADIUS;
        }
        Object raw = parameters.get(PARAM_RADIUS);
        if (raw instanceof Number n) {
            return Math.max(1, n.intValue());
        }
        return DEFAULT_BLUR_RADIUS;
    }

    /**
     * Applies 3-pass separable box blur in-place on straight ARGB pixels using premultiplied
     * alpha internally to avoid dark fringes around transparent regions.
     */
    public static void blurArgb(int[] argb, int width, int height, int radius) {
        if (width <= 0 || height <= 0 || radius < 1) {
            return;
        }
        int length = width * height;
        for (int i = 0; i < length; i++) {
            int p = argb[i];
            int a = (p >>> 24) & 0xFF;
            if (a == 0) {
                argb[i] = 0;
            } else if (a < 255) {
                int r = (((p >>> 16) & 0xFF) * a + 127) / 255;
                int g = (((p >>> 8) & 0xFF) * a + 127) / 255;
                int b = ((p & 0xFF) * a + 127) / 255;
                argb[i] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }

        int[] tmp = new int[length];
        blurPremultiplied(argb, tmp, width, height, radius);

        for (int i = 0; i < length; i++) {
            int p = argb[i];
            int a = (p >>> 24) & 0xFF;
            if (a == 0) {
                argb[i] = 0;
            } else if (a < 255) {
                int half = a / 2;
                int r = clamp((((p >>> 16) & 0xFF) * 255 + half) / a);
                int g = clamp((((p >>> 8) & 0xFF) * 255 + half) / a);
                int b = clamp(((p & 0xFF) * 255 + half) / a);
                argb[i] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
    }

    /**
     * Applies 3-pass separable box blur in-place on premultiplied ARGB pixels.
     */
    public static void blurPremultiplied(int[] src, int[] tmp, int width, int height, int radius) {
        if (width <= 0 || height <= 0 || radius < 1) {
            return;
        }
        for (int boxRadius : boxRadii(radius, BOX_PASSES)) {
            if (boxRadius > 0) {
                boxBlurHorizontal(src, tmp, width, height, boxRadius);
                boxBlurVertical(tmp, src, width, height, boxRadius);
            }
        }
    }

    public static int clamp(int value) {
        return value < 0 ? 0 : Math.min(255, value);
    }

    private static int[] boxRadii(double sigma, int passes) {
        double idealWidth = Math.sqrt((12.0 * sigma * sigma / passes) + 1.0);
        int wl = (int) Math.floor(idealWidth);
        if ((wl & 1) == 0) {
            wl--;
        }
        int wu = wl + 2;
        double idealM = (12.0 * sigma * sigma - passes * wl * wl - 4.0 * passes * wl - 3.0 * passes)
                / (-4.0 * wl - 4.0);
        int m = (int) Math.round(idealM);
        int[] radii = new int[passes];
        for (int i = 0; i < passes; i++) {
            int boxWidth = i < m ? wl : wu;
            radii[i] = (boxWidth - 1) / 2;
        }
        return radii;
    }

    private static void boxBlurHorizontal(int[] src, int[] dst, int width, int height, int r) {
        if (width <= 1) {
            System.arraycopy(src, 0, dst, 0, width * height);
            return;
        }
        int div = 2 * r + 1;
        int half = div / 2;
        int lastX = width - 1;
        for (int y = 0; y < height; y++) {
            int row = y * width;
            int first = src[row];
            int sumA = (r + 1) * ((first >>> 24) & 0xFF);
            int sumR = (r + 1) * ((first >>> 16) & 0xFF);
            int sumG = (r + 1) * ((first >>> 8) & 0xFF);
            int sumB = (r + 1) * (first & 0xFF);
            for (int i = 1; i <= r; i++) {
                int p = src[row + Math.min(i, lastX)];
                sumA += (p >>> 24) & 0xFF;
                sumR += (p >>> 16) & 0xFF;
                sumG += (p >>> 8) & 0xFF;
                sumB += p & 0xFF;
            }
            for (int x = 0; x < width; x++) {
                dst[row + x] = (((sumA + half) / div) << 24)
                        | (((sumR + half) / div) << 16)
                        | (((sumG + half) / div) << 8)
                        | ((sumB + half) / div);
                int outX = x - r;
                int inX = x + r + 1;
                int outP = src[row + (outX < 0 ? 0 : outX)];
                int inP = src[row + (inX > lastX ? lastX : inX)];
                sumA += ((inP >>> 24) & 0xFF) - ((outP >>> 24) & 0xFF);
                sumR += ((inP >>> 16) & 0xFF) - ((outP >>> 16) & 0xFF);
                sumG += ((inP >>> 8) & 0xFF) - ((outP >>> 8) & 0xFF);
                sumB += (inP & 0xFF) - (outP & 0xFF);
            }
        }
    }

    private static void boxBlurVertical(int[] src, int[] dst, int width, int height, int r) {
        if (height <= 1) {
            System.arraycopy(src, 0, dst, 0, width * height);
            return;
        }
        int div = 2 * r + 1;
        int half = div / 2;
        int lastY = height - 1;
        int[] sumA = new int[width];
        int[] sumR = new int[width];
        int[] sumG = new int[width];
        int[] sumB = new int[width];

        for (int x = 0; x < width; x++) {
            int first = src[x];
            sumA[x] = (r + 1) * ((first >>> 24) & 0xFF);
            sumR[x] = (r + 1) * ((first >>> 16) & 0xFF);
            sumG[x] = (r + 1) * ((first >>> 8) & 0xFF);
            sumB[x] = (r + 1) * (first & 0xFF);
        }
        for (int i = 1; i <= r; i++) {
            int row = Math.min(i, lastY) * width;
            for (int x = 0; x < width; x++) {
                int p = src[row + x];
                sumA[x] += (p >>> 24) & 0xFF;
                sumR[x] += (p >>> 16) & 0xFF;
                sumG[x] += (p >>> 8) & 0xFF;
                sumB[x] += p & 0xFF;
            }
        }
        for (int y = 0; y < height; y++) {
            int dstRow = y * width;
            int outRow = Math.max(0, y - r) * width;
            int inRow = Math.min(lastY, y + r + 1) * width;
            for (int x = 0; x < width; x++) {
                dst[dstRow + x] = (((sumA[x] + half) / div) << 24)
                        | (((sumR[x] + half) / div) << 16)
                        | (((sumG[x] + half) / div) << 8)
                        | ((sumB[x] + half) / div);
                int outP = src[outRow + x];
                int inP = src[inRow + x];
                sumA[x] += ((inP >>> 24) & 0xFF) - ((outP >>> 24) & 0xFF);
                sumR[x] += ((inP >>> 16) & 0xFF) - ((outP >>> 16) & 0xFF);
                sumG[x] += ((inP >>> 8) & 0xFF) - ((outP >>> 8) & 0xFF);
                sumB[x] += (inP & 0xFF) - (outP & 0xFF);
            }
        }
    }
}
