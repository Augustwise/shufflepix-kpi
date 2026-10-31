package com.shufflepix.client.core;

import com.shufflepix.client.core.style.Color;
import com.shufflepix.client.core.style.Style;
import com.shufflepix.client.core.style.StyleFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class StyleFactoryTest {
    @Test
    void sameParametersGiveSameInstance() {
        StyleFactory factory = new StyleFactory();
        Style a = factory.get(Color.BLACK, null, 2, "System", 16);
        Style b = factory.get(new Color(0, 0, 0, 255), null, 2, "System", 16);
        assertSame(a, b);
        assertEquals(1, factory.size());
    }

    @Test
    void differentParametersGiveDifferentInstances() {
        StyleFactory factory = new StyleFactory();
        Style a = factory.get(Color.BLACK, null, 2, "System", 16);
        Style b = factory.get(Color.BLACK, Color.WHITE, 2, "System", 16);
        assertNotSame(a, b);
        assertEquals(2, factory.size());
    }

    @Test
    void colorHexRoundTrip() {
        Color c = Color.fromHex("#12AB34CD");
        assertEquals(new Color(0x12, 0xAB, 0x34, 0xCD), c);
        assertEquals("#12AB34CD", c.toHex());
    }
}
