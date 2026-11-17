package com.shufflepix.client.io;

import com.shufflepix.client.core.effect.EffectRegistry;
import com.shufflepix.client.core.effect.NegativeEffect;
import com.shufflepix.client.core.image.RasterImage;
import com.shufflepix.client.core.model.Layer;
import com.shufflepix.client.core.model.Project;
import com.shufflepix.client.core.render.LayerCompositor;
import com.shufflepix.client.core.style.Color;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageFileServiceTest {
    private static final Color RED = new Color(255, 0, 0, 255);
    private static final Color GREEN = new Color(0, 200, 0, 255);
    private static final Color BLUE = new Color(0, 80, 220, 255);
    private static final Color SEMI_RED = new Color(255, 0, 0, 128);

    private final ImageFileService service = new ImageFileService();

    @Test
    void writesAndReadsPngPreservingAlphaChannel(@TempDir Path tempDir) throws IOException {
        WritableImage source = createAlphaTestImage();
        ImageFile pngFile = ImageFile.of(tempDir.resolve("sample.png"));

        service.write(source, pngFile, BLUE);

        // Verify raw file on disk has alpha channel via ImageIO directly (NFR-07).
        BufferedImage diskImage = ImageIO.read(pngFile.path().toFile());
        assertTrue(diskImage.getColorModel().hasAlpha(), "PNG має зберігати альфа-канал");

        Image loaded = service.read(pngFile);
        assertEquals(4, (int) loaded.getWidth());
        assertEquals(2, (int) loaded.getHeight());
        PixelReader reader = loaded.getPixelReader();

        assertEquals(RED.toArgb(), reader.getArgb(0, 0));
        assertEquals(GREEN.toArgb(), reader.getArgb(1, 0));
        assertEquals(0, alpha(reader.getArgb(2, 0)));
        assertEquals(128, alpha(reader.getArgb(3, 0)));
        assertNear(255, red(reader.getArgb(3, 0)), 2);
        assertEquals(0, green(reader.getArgb(3, 0)));
        assertEquals(0, blue(reader.getArgb(3, 0)));
    }

    @Test
    void writesAndReadsBmpFillingTransparencyWithBackground(@TempDir Path tempDir) throws IOException {
        WritableImage source = createAlphaTestImage();
        Color customBg = new Color(40, 100, 180, 255);
        ImageFile bmpFile = ImageFile.of(tempDir.resolve("sample.bmp"));

        service.write(source, bmpFile, customBg);

        BufferedImage diskImage = ImageIO.read(bmpFile.path().toFile());
        assertFalse(diskImage.getColorModel().hasAlpha(), "BMP не повинен містити альфа-канал");

        Image loaded = service.read(bmpFile);
        PixelReader reader = loaded.getPixelReader();

        assertEquals(RED.toArgb(), reader.getArgb(0, 0));
        assertEquals(GREEN.toArgb(), reader.getArgb(1, 0));
        // Fully transparent pixel becomes custom background color.
        assertEquals(customBg.toArgb(), reader.getArgb(2, 0));
        // Semi-transparent red (a=128) blended over (40, 100, 180) -> ~(148, 50, 90, 255).
        int blended = reader.getArgb(3, 0);
        assertEquals(255, alpha(blended));
        assertNear(148, red(blended), 3);
        assertNear(50, green(blended), 3);
        assertNear(90, blue(blended), 3);
    }

    @Test
    void writesAndReadsJpgAndJpegFillingTransparencyWithBackground(@TempDir Path tempDir) throws IOException {
        WritableImage source = new WritableImage(32, 32);
        PixelWriter writer = source.getPixelWriter();
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                writer.setArgb(x, y, x < 16 ? Color.TRANSPARENT.toArgb() : BLUE.toArgb());
            }
        }

        Color customBg = new Color(240, 220, 40, 255);
        ImageFile jpgFile = ImageFile.of(tempDir.resolve("photo.JPG"));
        service.write(source, jpgFile, customBg);

        BufferedImage diskImage = ImageIO.read(jpgFile.path().toFile());
        assertFalse(diskImage.getColorModel().hasAlpha(), "JPG не повинен містити альфа-канал");

        Image loaded = service.read(jpgFile);
        assertEquals(32, (int) loaded.getWidth());
        assertEquals(32, (int) loaded.getHeight());
        PixelReader reader = loaded.getPixelReader();

        // JPEG is lossy, so inspect interior pixels away from the seam.
        int bgPixel = reader.getArgb(4, 16);
        assertEquals(255, alpha(bgPixel));
        assertNear(240, red(bgPixel), 10);
        assertNear(220, green(bgPixel), 10);
        assertNear(40, blue(bgPixel), 10);

        int bluePixel = reader.getArgb(26, 16);
        assertEquals(255, alpha(bluePixel));
        assertNear(0, red(bluePixel), 10);
        assertNear(80, green(bluePixel), 10);
        assertNear(220, blue(bluePixel), 10);

        // Also verify .jpeg extension is accepted.
        ImageFile jpegFile = ImageFile.of(tempDir.resolve("photo.jpeg"));
        service.write(source, jpegFile, Color.WHITE);
        assertEquals(32, (int) service.read(jpegFile).getWidth());
    }

    @Test
    void bmpAndJpgFallBackToWhiteWhenBackgroundIsTransparentOrNull(@TempDir Path tempDir) {
        WritableImage transparent = new WritableImage(8, 8);

        ImageFile bmpNullBg = ImageFile.of(tempDir.resolve("null_bg.bmp"));
        service.write(transparent, bmpNullBg, null);
        assertEquals(Color.WHITE.toArgb(), service.read(bmpNullBg).getPixelReader().getArgb(4, 4));

        ImageFile bmpTransparentBg = ImageFile.of(tempDir.resolve("trans_bg.bmp"));
        service.write(transparent, bmpTransparentBg, Color.TRANSPARENT);
        assertEquals(Color.WHITE.toArgb(), service.read(bmpTransparentBg).getPixelReader().getArgb(4, 4));

        // Explicit opaque black background must remain black!
        ImageFile bmpBlackBg = ImageFile.of(tempDir.resolve("black_bg.bmp"));
        service.write(transparent, bmpBlackBg, Color.BLACK);
        assertEquals(Color.BLACK.toArgb(), service.read(bmpBlackBg).getPixelReader().getArgb(4, 4));
    }

    @Test
    void encodesAndDecodesPngLosslesslyWithAlpha() {
        WritableImage source = createAlphaTestImage();
        byte[] png = service.encodePng(source);

        // Standard PNG signature: 89 50 4E 47 0D 0A 1A 0A
        assertTrue(png.length > 8);
        assertEquals((byte) 0x89, png[0]);
        assertEquals((byte) 'P', png[1]);
        assertEquals((byte) 'N', png[2]);
        assertEquals((byte) 'G', png[3]);

        Image decoded = service.decodePng(png);
        assertEquals(4, (int) decoded.getWidth());
        assertEquals(2, (int) decoded.getHeight());
        PixelReader reader = decoded.getPixelReader();
        assertEquals(RED.toArgb(), reader.getArgb(0, 0));
        assertEquals(GREEN.toArgb(), reader.getArgb(1, 0));
        assertEquals(0, alpha(reader.getArgb(2, 0)));
        assertEquals(128, alpha(reader.getArgb(3, 0)));
    }

    @Test
    void readsExternalGrayscaleAndIndexedTransparentImages(@TempDir Path tempDir) throws IOException {
        // Grayscale JPEG
        BufferedImage gray = new BufferedImage(16, 16, BufferedImage.TYPE_BYTE_GRAY);
        gray.getRaster().setSample(8, 8, 0, 200);
        Path grayJpg = tempDir.resolve("gray.jpg");
        assertTrue(ImageIO.write(gray, "jpg", grayJpg.toFile()));

        Image loadedGray = service.read(ImageFile.of(grayJpg));
        assertEquals(16, (int) loadedGray.getWidth());
        assertEquals(16, (int) loadedGray.getHeight());

        // Indexed PNG with tRNS transparent index
        byte[] r = {0, (byte) 255};
        byte[] g = {0, 0};
        byte[] b = {0, 0};
        IndexColorModel icm = new IndexColorModel(8, 2, r, g, b, 0);
        BufferedImage indexed = new BufferedImage(4, 4, BufferedImage.TYPE_BYTE_INDEXED, icm);
        indexed.getRaster().setSample(0, 0, 0, 0); // transparent
        indexed.getRaster().setSample(1, 0, 0, 1); // opaque red
        Path indexedPng = tempDir.resolve("indexed.png");
        assertTrue(ImageIO.write(indexed, "png", indexedPng.toFile()));

        Image loadedIndexed = service.read(ImageFile.of(indexedPng));
        assertEquals(0, alpha(loadedIndexed.getPixelReader().getArgb(0, 0)));
        assertEquals(RED.toArgb(), loadedIndexed.getPixelReader().getArgb(1, 0));
    }

    @Test
    void rejectsUnsupportedFormatsIncludingDisguisedGif(@TempDir Path tempDir) throws IOException {
        assertThrows(ImageFileException.class, () -> ImageFile.of(null));
        assertThrows(ImageFileException.class, () -> ImageFile.of(tempDir.resolve("animation.gif")));
        assertThrows(ImageFileException.class, () -> ImageFile.of(tempDir.resolve("document.txt")));
        assertThrows(ImageFileException.class, () -> ImageFile.of(tempDir.resolve("no_extension")));

        // Valid GIF bytes disguised with a .png extension must be rejected (NFR-04).
        BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        Path disguisedGif = tempDir.resolve("disguised.png");
        assertTrue(ImageIO.write(img, "gif", disguisedGif.toFile()));

        assertThrows(ImageFileException.class, () -> service.read(ImageFile.of(disguisedGif)));
    }

    @Test
    void rejectsCorruptedEmptyTruncatedOversizedAndMissingFiles(@TempDir Path tempDir) throws IOException {
        // Missing file
        assertThrows(ImageFileException.class, () -> service.read(ImageFile.of(tempDir.resolve("missing.png"))));

        // Directory masquerading as file name
        Path dirAsPng = Files.createDirectory(tempDir.resolve("folder.png"));
        assertThrows(ImageFileException.class, () -> service.read(ImageFile.of(dirAsPng)));

        // Empty 0-byte file
        Path emptyPng = Files.createFile(tempDir.resolve("empty.png"));
        assertThrows(ImageFileException.class, () -> service.read(ImageFile.of(emptyPng)));

        // Plain text / random garbage renamed to .png, .jpg, .bmp
        for (String name : new String[]{"text.png", "text.jpg", "text.bmp"}) {
            Path garbage = tempDir.resolve(name);
            Files.writeString(garbage, "Це не зображення, а звичайний текст", StandardCharsets.UTF_8);
            assertThrows(ImageFileException.class, () -> service.read(ImageFile.of(garbage)));
        }

        // Truncated PNG, BMP, JPG (both early header truncation and mid-stream truncation)
        WritableImage sample = new WritableImage(64, 64);
        PixelWriter sampleWriter = sample.getPixelWriter();
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                sampleWriter.setArgb(x, y, (0xFF << 24) | ((x * 4) << 16) | ((y * 4) << 8) | ((x + y) & 0xFF));
            }
        }
        for (String ext : new String[]{"png", "bmp", "jpg"}) {
            ImageFile valid = ImageFile.of(tempDir.resolve("valid." + ext));
            service.write(sample, valid, Color.WHITE);
            byte[] full = Files.readAllBytes(valid.path());

            Path headTruncated = tempDir.resolve("head_truncated." + ext);
            Files.write(headTruncated, Arrays.copyOf(full, Math.min(full.length / 2, 24)));
            assertThrows(ImageFileException.class, () -> service.read(ImageFile.of(headTruncated)),
                    "Обірваний на початку " + ext + " має викликати ImageFileException");

            Path midTruncated = tempDir.resolve("mid_truncated." + ext);
            Files.write(midTruncated, Arrays.copyOf(full, (full.length * 2) / 3));
            assertThrows(ImageFileException.class, () -> service.read(ImageFile.of(midTruncated)),
                    "Обірваний посередині " + ext + " має викликати ImageFileException");
        }

        // Crafted PNG header claiming 9000x9000 (> Limits.MAX_CANVAS_SIZE) must fail safely without OOM.
        Path oversizedPng = tempDir.resolve("oversized.png");
        Files.write(oversizedPng, createOversizedPngHeader(9000, 9000));
        assertThrows(ImageFileException.class, () -> service.read(ImageFile.of(oversizedPng)));

        // decodePng validation
        assertThrows(ImageFileException.class, () -> service.decodePng(null));
        assertThrows(ImageFileException.class, () -> service.decodePng(new byte[0]));
        assertThrows(ImageFileException.class, () -> service.decodePng("not a png".getBytes(StandardCharsets.UTF_8)));

        // Passing JPEG bytes to decodePng must be rejected
        Path jpgPath = tempDir.resolve("valid.jpg");
        byte[] jpgBytes = Files.readAllBytes(jpgPath);
        assertThrows(ImageFileException.class, () -> service.decodePng(jpgBytes));
    }

    @Test
    void handlesWriteFailuresCleanlyWithoutCreatingFile(@TempDir Path tempDir) throws IOException {
        WritableImage image = RasterImage.filled(8, 8, RED).getPixels();

        // Missing parent directory
        Path missingParentTarget = tempDir.resolve("nonexistent_dir").resolve("out.png");
        assertThrows(ImageFileException.class, () -> service.write(image, ImageFile.of(missingParentTarget), Color.WHITE));
        assertFalse(Files.exists(missingParentTarget));

        // Target path is an existing directory
        Path dirTarget = Files.createDirectory(tempDir.resolve("dir_target.png"));
        assertThrows(ImageFileException.class, () -> service.write(image, ImageFile.of(dirTarget), Color.WHITE));

        // Null inputs
        assertThrows(ImageFileException.class, () -> service.write(null, ImageFile.of(tempDir.resolve("a.png")), Color.WHITE));
        assertThrows(ImageFileException.class, () -> service.write(image, null, Color.WHITE));
        assertThrows(ImageFileException.class, () -> service.encodePng(null));
    }

    @Test
    void endToEndComposeExportAndReopenFlow(@TempDir Path tempDir) {
        Project project = new Project(20, 20, new Color(250, 240, 230, 255));
        Layer base = Layer.transparent("Base", 20, 20);
        PixelWriter baseWriter = ((RasterImage) base.getContent()).getPixels().getPixelWriter();
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 20; x++) {
                baseWriter.setArgb(x, y, RED.toArgb());
            }
        }
        base.applyEffect(EffectRegistry.withDefaults(), NegativeEffect.CODE, Map.of());
        project.addLayer(0, base);

        Layer hidden = new Layer("Hidden", RasterImage.filled(20, 20, GREEN));
        hidden.setVisible(false);
        project.addLayer(1, hidden);

        LayerCompositor compositor = new LayerCompositor();

        // Export PNG: top half is inverted red (cyan 0,255,255), bottom half stays transparent
        ImageFile pngOut = ImageFile.of(tempDir.resolve("export.png"));
        WritableImage composedPng = compositor.compose(project, Color.TRANSPARENT);
        service.write(composedPng, pngOut, Color.TRANSPARENT);

        Image reopenedPng = service.read(pngOut);
        assertEquals(new Color(0, 255, 255, 255).toArgb(), reopenedPng.getPixelReader().getArgb(5, 5));
        assertEquals(0, alpha(reopenedPng.getPixelReader().getArgb(5, 15)));

        // Export BMP: top half is inverted red (cyan), bottom half filled with project background
        ImageFile bmpOut = ImageFile.of(tempDir.resolve("export.bmp"));
        Color bg = ImageFileService.opaqueBackground(project.getBackgroundColor());
        WritableImage composedBmp = compositor.compose(project, bg);
        service.write(composedBmp, bmpOut, bg);

        Image reopenedBmp = service.read(bmpOut);
        assertEquals(new Color(0, 255, 255, 255).toArgb(), reopenedBmp.getPixelReader().getArgb(5, 5));
        assertEquals(project.getBackgroundColor().toArgb(), reopenedBmp.getPixelReader().getArgb(5, 15));

        // Original layer pixels and properties remained untouched.
        assertEquals(RED.toArgb(), base.getContent().getImage().getPixelReader().getArgb(5, 5));
        assertFalse(hidden.isVisible());
        assertEquals(1, base.getEffects().size());
    }

    private static WritableImage createAlphaTestImage() {
        WritableImage image = new WritableImage(4, 2);
        PixelWriter writer = image.getPixelWriter();
        for (int y = 0; y < 2; y++) {
            writer.setArgb(0, y, RED.toArgb());
            writer.setArgb(1, y, GREEN.toArgb());
            writer.setArgb(2, y, Color.TRANSPARENT.toArgb());
            writer.setArgb(3, y, SEMI_RED.toArgb());
        }
        return image;
    }

    private static byte[] createOversizedPngHeader(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // PNG magic signature
        out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        // IHDR chunk (13 bytes payload)
        byte[] ihdrData = ByteBuffer.allocate(13)
                .putInt(width)
                .putInt(height)
                .put((byte) 8) // bit depth
                .put((byte) 6) // RGBA
                .put((byte) 0) // compression
                .put((byte) 0) // filter
                .put((byte) 0) // interlace
                .array();
        writePngChunk(out, "IHDR", ihdrData);
        writePngChunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void writePngChunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        out.write(ByteBuffer.allocate(4).putInt(data.length).array());
        out.write(typeBytes);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        out.write(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }

    private static void assertNear(int expected, int actual, int tolerance) {
        assertTrue(Math.abs(expected - actual) <= tolerance,
                "Очікувалося " + expected + " (±" + tolerance + "), отримано " + actual);
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
