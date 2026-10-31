package com.shufflepix.client.core.style;

import java.util.HashMap;
import java.util.Map;

public class StyleFactory {
    private final Map<Style, Style> pool = new HashMap<>();

    public Style get(Color stroke, Color fill, double lineWidth, String fontFamily, double fontSize) {
        Style candidate = new Style(stroke, fill, lineWidth, fontFamily, fontSize);
        return pool.computeIfAbsent(candidate, s -> s);
    }

    public int size() {
        return pool.size();
    }
}
