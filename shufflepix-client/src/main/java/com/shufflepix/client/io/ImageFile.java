package com.shufflepix.client.io;

import java.nio.file.Path;

public record ImageFile(Path path, ImageFormat format) {
    public static ImageFile of(Path path) {
        if (path == null) {
            throw new ImageFileException("Не вказано шлях до файлу");
        }
        ImageFormat format = ImageFormat.fromPath(path)
                .orElseThrow(() -> new ImageFileException("Непідтримуваний формат файлу: "
                        + (path.getFileName() != null ? path.getFileName() : path)));
        return new ImageFile(path, format);
    }
}
