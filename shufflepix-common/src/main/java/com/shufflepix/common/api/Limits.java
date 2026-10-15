package com.shufflepix.common.api;

public final class Limits {
    public static final int MAX_CANVAS_SIZE = 8192;

    public static final int MAX_NAME_LENGTH = 100;

    public static final int MAX_LAYER_PNG_BYTES = 20 * 1024 * 1024;

    public static final int MAX_THUMBNAIL_SIZE = 256;

    public static final String COLOR_PATTERN = "^#[0-9A-F]{8}$";

    private Limits() {
    }
}
