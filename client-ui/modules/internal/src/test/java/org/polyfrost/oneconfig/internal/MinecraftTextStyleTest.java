package org.polyfrost.oneconfig.internal;

import org.junit.jupiter.api.Test;
import org.polyfrost.compose.mc.MinecraftTextStyle;
import static org.junit.jupiter.api.Assertions.*;

class MinecraftTextStyleTest {
    @Test
    void colorsPreserveAlphaAndResetAllFormatting() {
        MinecraftTextStyle style = new MinecraftTextStyle(0x7F123456);
        for (char code : "klmno".toCharArray()) style.apply(code);
        assertTrue(style.getBold());
        assertTrue(style.getItalic());
        assertTrue(style.getUnderline());
        assertTrue(style.getStrikethrough());
        assertTrue(style.getObfuscated());
        style.apply('C');
        assertEquals(0x7FFF5555, style.getColor());
        assertFalse(style.getBold());
        assertFalse(style.getItalic());
        assertFalse(style.getUnderline());
        assertFalse(style.getStrikethrough());
        assertFalse(style.getObfuscated());
        style.apply('6');
        assertEquals(0x7FFFAA00, style.getColor());
        style.apply('R');
        assertEquals(0x7F123456, style.getColor());
    }
}
