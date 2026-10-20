// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package org.polyfrost.oneconfig.internal.ui.hud;

import java.util.List;
import kotlin.jvm.functions.Function2;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.polyfrost.compose.layout.PolyAlign;
import org.polyfrost.compose.layout.PolyInsets;
import org.polyfrost.compose.layout.PolyLayoutEngine;
import org.polyfrost.compose.layout.PolySize;
import org.polyfrost.compose.mc.McFontQueue;
import org.polyfrost.compose.mc.MinecraftTextStyle;
import org.polyfrost.compose.node.McTextNode;
import org.polyfrost.compose.node.PolyNode;

import static org.junit.jupiter.api.Assertions.*;

class TextAlignmentTest {
    private Function2<? super String, ? super Float, Float> previousMeasure;

    @BeforeEach
    void setMetrics() {
        previousMeasure = McFontQueue.INSTANCE.getMeasureWidth();
        McFontQueue.INSTANCE.setMeasureWidth((text, scale) -> text.replaceAll("§.", "").length() * 6f * scale);
    }

    @AfterEach
    void restoreMetrics() {
        McFontQueue.INSTANCE.setMeasureWidth(previousMeasure);
    }

    @Test
    void multilineLayoutCarriesColorAndFormattingAcrossBlankLines() {
        List<McFontQueue.Line> lines = McFontQueue.INSTANCE.layoutLines("§A§lLong\n\nS", 2f);
        assertEquals(3, lines.size());
        assertEquals("§a§l", lines.get(1).getText());
        assertEquals("§a§lS", lines.get(2).getText());
        assertEquals(48f, lines.get(0).getWidth());
        assertEquals(0f, lines.get(1).getWidth());
        assertEquals(12f, lines.get(2).getWidth());
    }

    @Test
    void resetsAndNewColorsStopPreviousLineFormatting() {
        List<McFontQueue.Line> lines = McFontQueue.INSTANCE.layoutLines("§c§lA\n§rB\nC\n§9D\nE", 1f);
        assertEquals("C", lines.get(2).getText());
        assertEquals("§9E", lines.get(4).getText());
        MinecraftTextStyle style = new MinecraftTextStyle(0xFF123456);
        style.apply('a');
        style.apply('l');
        style.apply('o');
        style.apply('n');
        assertEquals("§a§l§n§o", style.formatPrefix());
        style.apply('r');
        assertEquals("", style.formatPrefix());
        assertEquals(0xFF123456, style.getColor());
    }

    @Test
    void fixedHudWidthKeepsPaddingAndAlignsEveryLine() {
        PolyNode box = new PolyNode();
        box.getStyle().setWidth(new PolySize.Px(100f));
        box.getStyle().setHeight(new PolySize.Px(60f));
        box.getStyle().setPadding(new PolyInsets(4f, 4f, 4f, 4f));
        McTextNode text = new McTextNode();
        text.setText("abcd\na");
        text.getStyle().setTextWidth(92f);
        text.getStyle().setAlign(PolyAlign.Center);
        text.remeasure();
        box.getChildren().add(text);
        PolyLayoutEngine.INSTANCE.layout(box, 100f, 60f);
        assertEquals(92f, text.getWidth());
        assertEquals(4f, text.getX());
        assertEquals(0f, McFontQueue.INSTANCE.lineOffset(text.getWidth(), 24f, 0));
        assertEquals(34f, McFontQueue.INSTANCE.lineOffset(text.getWidth(), 24f, 1));
        assertEquals(68f, McFontQueue.INSTANCE.lineOffset(text.getWidth(), 24f, 2));
        assertEquals(86f, McFontQueue.INSTANCE.lineOffset(text.getWidth(), 6f, 2));
    }

    @Test
    void automaticWidthUsesLongestLineAndDoesNotMoveOverflowLeft() {
        McTextNode text = new McTextNode();
        text.setText("abc\na");
        text.remeasure();
        PolyLayoutEngine.INSTANCE.layout(text, 100f, 50f);
        assertEquals(18f, text.getWidth());
        assertEquals(6f, McFontQueue.INSTANCE.lineOffset(text.getWidth(), 6f, 1));
        assertEquals(12f, McFontQueue.INSTANCE.lineOffset(text.getWidth(), 6f, 2));
        assertEquals(0f, McFontQueue.INSTANCE.lineOffset(10f, 18f, 2));
    }
}
