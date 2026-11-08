package com.shufflepix.client.core.shape;

import com.shufflepix.client.core.style.Style;

import java.util.Arrays;

public class FreehandStroke extends Primitive {
    private double[] xs = new double[16];
    private double[] ys = new double[16];
    private int size;
    private final boolean eraser;

    public FreehandStroke(ShapeRenderer renderer, Style style, boolean eraser) {
        super(renderer, style);
        this.eraser = eraser;
    }

    public void addPoint(double x, double y) {
        if (size == xs.length) {
            xs = Arrays.copyOf(xs, size * 2);
            ys = Arrays.copyOf(ys, size * 2);
        }
        xs[size] = x;
        ys[size] = y;
        size++;
    }

    @Override
    public void draw() {
        double[] px = Arrays.copyOf(xs, size);
        double[] py = Arrays.copyOf(ys, size);
        if (eraser) {
            renderer.erasePolyline(px, py, style.getLineWidth());
        } else {
            renderer.drawPolyline(px, py, style);
        }
    }

    @Override
    public FreehandStroke copy() {
        FreehandStroke copy = new FreehandStroke(renderer, style, eraser);
        copy.xs = Arrays.copyOf(xs, xs.length);
        copy.ys = Arrays.copyOf(ys, ys.length);
        copy.size = size;
        return copy;
    }
}
