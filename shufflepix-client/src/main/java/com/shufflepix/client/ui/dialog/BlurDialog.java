package com.shufflepix.client.ui.dialog;

import com.shufflepix.client.core.effect.BlurEffect;
import com.shufflepix.client.core.image.RasterImage;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.ImagePattern;
import javafx.stage.Window;

public class BlurDialog extends Dialog<Integer> {
    private static final int MAX_PREVIEW_WIDTH = 360;
    private static final int MAX_PREVIEW_HEIGHT = 240;
    private static final int CHECKER_SIZE = 8;

    public BlurDialog(Window owner) {
        this(owner, null);
    }

    public BlurDialog(Window owner, Image sourceImage) {
        initOwner(owner);
        setTitle("Розмиття");

        Slider radius = new Slider(1, 50, BlurEffect.DEFAULT_RADIUS);
        radius.setShowTickLabels(true);
        radius.setMajorTickUnit(10);
        radius.setSnapToTicks(false);
        Label value = new Label();
        value.textProperty().bind(radius.valueProperty().asString("Радіус: %.0f пікселів"));

        VBox content = new VBox(8);
        if (sourceImage != null && sourceImage.getWidth() > 0 && sourceImage.getHeight() > 0) {
            double scale = Math.min(1.0, Math.min(
                    MAX_PREVIEW_WIDTH / sourceImage.getWidth(),
                    MAX_PREVIEW_HEIGHT / sourceImage.getHeight()));
            RasterImage previewSource = scaleForPreview(sourceImage, scale);

            ImageView previewView = new ImageView();
            previewView.setPreserveRatio(true);
            StackPane previewFrame = new StackPane(previewView);
            previewFrame.setAlignment(Pos.CENTER);
            previewFrame.setPrefSize(previewSource.getWidth(), previewSource.getHeight());
            previewFrame.setMaxSize(previewSource.getWidth(), previewSource.getHeight());
            previewFrame.setBackground(new Background(new BackgroundFill(createCheckerboard(), null, null)));
            previewFrame.setStyle("-fx-border-color: #b0b0b0; -fx-border-width: 1px;");

            StackPane previewContainer = new StackPane(previewFrame);
            previewContainer.setAlignment(Pos.CENTER);

            int[] lastPreviewRadius = {-1};
            Runnable updatePreview = () -> {
                int r = (int) Math.round(radius.getValue());
                int previewRadius = Math.max(1, (int) Math.round(r * scale));
                if (previewRadius == lastPreviewRadius[0]) {
                    return;
                }
                lastPreviewRadius[0] = previewRadius;
                previewView.setImage(new BlurEffect(previewSource, previewRadius).getImage());
            };
            radius.valueProperty().addListener((obs, oldVal, newVal) -> updatePreview.run());
            updatePreview.run();

            content.getChildren().add(previewContainer);
        }

        content.getChildren().addAll(value, radius);
        getDialogPane().setContent(content);

        ButtonType ok = new ButtonType("Застосувати", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Скасувати", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(ok, cancel);
        setResultConverter(button -> button == ok ? (int) Math.round(radius.getValue()) : null);
    }

    private static RasterImage scaleForPreview(Image source, double scale) {
        int srcW = (int) source.getWidth();
        int srcH = (int) source.getHeight();
        if (scale >= 1.0) {
            return RasterImage.of(source);
        }
        int dstW = Math.max(1, (int) Math.round(srcW * scale));
        int dstH = Math.max(1, (int) Math.round(srcH * scale));

        int[] src = new int[srcW * srcH];
        source.getPixelReader().getPixels(0, 0, srcW, srcH, PixelFormat.getIntArgbPreInstance(), src, 0, srcW);
        int[] dst = new int[dstW * dstH];

        double xRatio = (double) srcW / dstW;
        double yRatio = (double) srcH / dstH;
        for (int y = 0; y < dstH; y++) {
            double sy = (y + 0.5) * yRatio - 0.5;
            int y0 = Math.max(0, Math.min(srcH - 1, (int) Math.floor(sy)));
            int y1 = Math.min(srcH - 1, y0 + 1);
            double fy = Math.max(0.0, sy - y0);
            int row0 = y0 * srcW;
            int row1 = y1 * srcW;
            for (int x = 0; x < dstW; x++) {
                double sx = (x + 0.5) * xRatio - 0.5;
                int x0 = Math.max(0, Math.min(srcW - 1, (int) Math.floor(sx)));
                int x1 = Math.min(srcW - 1, x0 + 1);
                double fx = Math.max(0.0, sx - x0);

                int p00 = src[row0 + x0];
                int p10 = src[row0 + x1];
                int p01 = src[row1 + x0];
                int p11 = src[row1 + x1];

                int a = bilerp((p00 >>> 24) & 0xFF, (p10 >>> 24) & 0xFF, (p01 >>> 24) & 0xFF, (p11 >>> 24) & 0xFF, fx, fy);
                int r = bilerp((p00 >>> 16) & 0xFF, (p10 >>> 16) & 0xFF, (p01 >>> 16) & 0xFF, (p11 >>> 16) & 0xFF, fx, fy);
                int g = bilerp((p00 >>> 8) & 0xFF, (p10 >>> 8) & 0xFF, (p01 >>> 8) & 0xFF, (p11 >>> 8) & 0xFF, fx, fy);
                int b = bilerp(p00 & 0xFF, p10 & 0xFF, p01 & 0xFF, p11 & 0xFF, fx, fy);
                dst[y * dstW + x] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }

        WritableImage out = new WritableImage(dstW, dstH);
        out.getPixelWriter().setPixels(0, 0, dstW, dstH, PixelFormat.getIntArgbPreInstance(), dst, 0, dstW);
        return new RasterImage(out);
    }

    private static int bilerp(int c00, int c10, int c01, int c11, double fx, double fy) {
        double top = c00 + (c10 - c00) * fx;
        double bottom = c01 + (c11 - c01) * fx;
        return (int) Math.round(top + (bottom - top) * fy);
    }

    private static ImagePattern createCheckerboard() {
        int tile = CHECKER_SIZE * 2;
        WritableImage img = new WritableImage(tile, tile);
        PixelWriter writer = img.getPixelWriter();
        for (int y = 0; y < tile; y++) {
            for (int x = 0; x < tile; x++) {
                boolean light = ((x / CHECKER_SIZE) + (y / CHECKER_SIZE)) % 2 == 0;
                writer.setArgb(x, y, light ? 0xFFFFFFFF : 0xFFDCDCDC);
            }
        }
        return new ImagePattern(img, 0, 0, tile, tile, false);
    }
}
