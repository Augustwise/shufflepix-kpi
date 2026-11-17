package com.shufflepix.client.io;

import com.shufflepix.client.core.style.Color;
import com.shufflepix.common.api.Limits;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;

import javax.imageio.IIOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

public class ImageFileService {
    private static final float JPEG_QUALITY = 0.92f;

    public Image read(ImageFile file) {
        if (file == null || file.path() == null || file.format() == null) {
            throw new ImageFileException("Не вказано файл зображення");
        }
        Path path = file.path();
        String fileName = displayName(path);
        if (!Files.exists(path)) {
            throw new ImageFileException("Файл не знайдено: " + fileName);
        }
        if (!Files.isRegularFile(path)) {
            throw new ImageFileException("Вказаний шлях не є файлом: " + fileName);
        }
        if (!Files.isReadable(path)) {
            throw new ImageFileException("Немає прав на читання файлу: " + fileName);
        }
        try {
            if (Files.size(path) == 0) {
                throw new ImageFileException("Файл порожній або пошкоджений: " + fileName);
            }
            try (ImageInputStream iis = ImageIO.createImageInputStream(path.toFile())) {
                if (iis == null) {
                    throw new ImageFileException("Не вдалося відкрити файл: " + fileName);
                }
                return readFromStream(iis, fileName, null);
            }
        } catch (ImageFileException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new ImageFileException("Не вдалося прочитати зображення «" + fileName + "»: " + describeError(e), e);
        }
    }

    public void write(Image image, ImageFile file, Color background) {
        if (image == null) {
            throw new ImageFileException("Зображення для збереження відсутнє");
        }
        if (file == null || file.path() == null || file.format() == null) {
            throw new ImageFileException("Не вказано файл для збереження");
        }

        BufferedImage argb = toArgbBufferedImage(image);
        BufferedImage output = file.format().supportsAlpha()
                ? argb
                : flattenToRgb(argb, opaqueBackground(background));

        Path target = file.path().toAbsolutePath().normalize();
        String fileName = displayName(file.path());
        if (Files.isDirectory(target)) {
            throw new ImageFileException("Не вдалося зберегти файл «" + fileName + "»: вказаний шлях є папкою");
        }
        Path parent = target.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new ImageFileException("Не вдалося зберегти файл «" + fileName + "»: папка призначення не існує");
        }

        Path tempFile = null;
        boolean moved = false;
        try {
            tempFile = Files.createTempFile(parent, ".shufflepix-", ".tmp");
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(tempFile.toFile())) {
                if (ios == null) {
                    throw new ImageFileException("Не вдалося відкрити файл для запису: " + fileName);
                }
                writeImage(output, file.format(), ios);
                ios.flush();
            }
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
            moved = true;
        } catch (ImageFileException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new ImageFileException("Не вдалося зберегти файл «" + fileName + "»: " + describeError(e), e);
        } finally {
            if (!moved && tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // Best-effort cleanup of incomplete temporary file
                }
            }
        }
    }

    public byte[] encodePng(Image image) {
        BufferedImage argb = toArgbBufferedImage(image);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
            writeImage(argb, ImageFormat.PNG, ios);
        } catch (ImageFileException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new ImageFileException("Не вдалося закодувати зображення у PNG: " + describeError(e), e);
        }
        return out.toByteArray();
    }

    public Image decodePng(byte[] png) {
        if (png == null || png.length == 0) {
            throw new ImageFileException("Порожні дані PNG");
        }
        try (ImageInputStream iis = new MemoryCacheImageInputStream(new ByteArrayInputStream(png))) {
            return readFromStream(iis, "PNG", ImageFormat.PNG);
        } catch (ImageFileException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new ImageFileException("Не вдалося декодувати PNG: " + describeError(e), e);
        }
    }

    public static Color opaqueBackground(Color background) {
        if (background == null || background.alpha() == 0) {
            return Color.WHITE;
        }
        if (background.alpha() == 255) {
            return background;
        }
        double alpha = background.alpha() / 255.0;
        double invAlpha = 1.0 - alpha;
        int r = (int) Math.round(background.red() * alpha + 255.0 * invAlpha);
        int g = (int) Math.round(background.green() * alpha + 255.0 * invAlpha);
        int b = (int) Math.round(background.blue() * alpha + 255.0 * invAlpha);
        return new Color(r, g, b, 255);
    }

    private static WritableImage readFromStream(ImageInputStream iis, String sourceName, ImageFormat expectedFormat)
            throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
        if (!readers.hasNext()) {
            throw new ImageFileException("Файл пошкоджений або має непідтримуваний формат: " + sourceName);
        }
        ImageReader reader = readers.next();
        try {
            String detectedName = reader.getFormatName();
            ImageFormat detected = ImageFormat.fromFormatName(detectedName)
                    .orElseThrow(() -> new ImageFileException(
                            "Непідтримуваний формат файлу (" + detectedName + "): " + sourceName));
            if (expectedFormat != null && detected != expectedFormat) {
                throw new ImageFileException(
                        "Очікувався формат " + expectedFormat.getDisplayName()
                                + ", але отримано " + detected.getDisplayName() + ": " + sourceName);
            }

            AtomicReference<String> fatalWarning = new AtomicReference<>();
            reader.addIIOReadWarningListener((source, warning) -> {
                if (isCorruptionWarning(warning)) {
                    fatalWarning.compareAndSet(null, warning);
                }
            });

            reader.setInput(iis, true, true);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            if (width < 1 || height < 1) {
                throw new ImageFileException("Некоректні розміри зображення у файлі: " + sourceName);
            }
            if (width > Limits.MAX_CANVAS_SIZE || height > Limits.MAX_CANVAS_SIZE) {
                throw new ImageFileException("Розміри зображення (" + width + "×" + height
                        + ") перевищують максимально допустимі (" + Limits.MAX_CANVAS_SIZE + "×"
                        + Limits.MAX_CANVAS_SIZE + "): " + sourceName);
            }

            BufferedImage buffered = reader.read(0);
            if (fatalWarning.get() != null) {
                throw new ImageFileException("Файл пошкоджений або обірваний: " + sourceName);
            }
            if (buffered == null) {
                throw new ImageFileException("Файл пошкоджений або не містить зображення: " + sourceName);
            }
            return SwingFXUtils.toFXImage(buffered, null);
        } finally {
            reader.dispose();
        }
    }

    private static BufferedImage toArgbBufferedImage(Image image) {
        if (image == null) {
            throw new ImageFileException("Зображення відсутнє");
        }
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        if (width <= 0 || height <= 0 || image.getPixelReader() == null) {
            throw new ImageFileException("Зображення порожнє або недоступне для читання");
        }
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        BufferedImage converted = SwingFXUtils.fromFXImage(image, target);
        if (converted == null) {
            throw new ImageFileException("Не вдалося прочитати пікселі зображення");
        }
        return converted;
    }

    private static BufferedImage flattenToRgb(BufferedImage argb, Color opaqueBg) {
        int width = argb.getWidth();
        int height = argb.getHeight();
        BufferedImage rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(new java.awt.Color(opaqueBg.red(), opaqueBg.green(), opaqueBg.blue()));
            g.fillRect(0, 0, width, height);
            g.setComposite(AlphaComposite.SrcOver);
            g.drawImage(argb, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    private static void writeImage(BufferedImage image, ImageFormat format, ImageOutputStream ios) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format.getImageIoName());
        if (!writers.hasNext()) {
            throw new ImageFileException("Формат не підтримується для запису: " + format.getDisplayName());
        }
        ImageWriter writer = writers.next();
        try {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (format == ImageFormat.JPG && param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                if (param.getCompressionType() == null) {
                    String[] types = param.getCompressionTypes();
                    if (types != null && types.length > 0) {
                        param.setCompressionType(types[0]);
                    }
                }
                param.setCompressionQuality(JPEG_QUALITY);
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    private static boolean isCorruptionWarning(String warning) {
        if (warning == null) {
            return false;
        }
        String lower = warning.toLowerCase(Locale.ROOT);
        return lower.contains("corrupt") || lower.contains("truncated") || lower.contains("premature end");
    }

    private static String displayName(Path path) {
        return path.getFileName() != null ? path.getFileName().toString() : path.toString();
    }

    private static String describeError(Throwable e) {
        if (e instanceof AccessDeniedException) {
            return "немає прав доступу";
        }
        if (e instanceof NoSuchFileException) {
            return "файл або папку не знайдено";
        }
        if (e instanceof EOFException) {
            return "файл обірваний або пошкоджений";
        }
        if (e instanceof IIOException) {
            return "файл пошкоджений або має некоректну структуру";
        }
        String message = e.getMessage();
        return (message == null || message.isBlank()) ? "помилка обробки файлу" : message;
    }
}
