package com.shufflepix.client.io;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum ImageFormat {
    BMP("BMP", "bmp", false, List.of("bmp")),
    PNG("PNG", "png", true, List.of("png")),
    JPG("JPG", "jpg", false, List.of("jpg", "jpeg"));

    private final String displayName;
    private final String imageIoName;
    private final boolean supportsAlpha;
    private final List<String> extensions;

    ImageFormat(String displayName, String imageIoName, boolean supportsAlpha, List<String> extensions) {
        this.displayName = displayName;
        this.imageIoName = imageIoName;
        this.supportsAlpha = supportsAlpha;
        this.extensions = extensions;
    }

    public static Optional<ImageFormat> fromPath(Path path) {
        if (path == null || path.getFileName() == null) {
            return Optional.empty();
        }
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        for (ImageFormat format : values()) {
            for (String ext : format.extensions) {
                if (fileName.endsWith("." + ext)) {
                    return Optional.of(format);
                }
            }
        }
        return Optional.empty();
    }

    public static Optional<ImageFormat> fromFormatName(String formatName) {
        if (formatName == null || formatName.isBlank()) {
            return Optional.empty();
        }
        String normalized = formatName.strip().toLowerCase(Locale.ROOT);
        for (ImageFormat format : values()) {
            if (format.imageIoName.equals(normalized) || format.extensions.contains(normalized)) {
                return Optional.of(format);
            }
        }
        return Optional.empty();
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getImageIoName() {
        return imageIoName;
    }

    public boolean supportsAlpha() {
        return supportsAlpha;
    }

    public List<String> getExtensions() {
        return extensions;
    }
}
