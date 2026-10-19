package org.polyfrost.oneconfig.internal.ui.themes;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ThemeAccentTest {
    @TempDir Path directory;

    @Test void includesExactly256DistinctNamedPresetsAndTheRequestedDefault() {
        assertEquals(256, GradientPresets.all().size());
        HashSet<String> names = new HashSet<>(), pairs = new HashSet<>();
        for (var preset : GradientPresets.all()) {
            assertTrue(names.add(preset.name()));
            assertTrue(pairs.add(preset.start() + ":" + preset.end()));
            assertNotEquals(preset.start(), preset.end());
            assertEquals(255, preset.start() >>> 24);
            assertEquals(255, preset.end() >>> 24);
        }
        assertEquals("Arcadium", GradientPresets.get(0).name());
        assertEquals(0xFF3AA0FF, GradientPresets.get(0).start());
        assertEquals(0xFF36F1B0, GradientPresets.get(0).end());
        assertSame(GradientPresets.get(0), GradientPresets.get(-1));
        assertSame(GradientPresets.get(0), GradientPresets.get(256));
    }

    @Test void preservesEndpointsAndLoopsWithoutAColorJump() {
        var theme = theme(true, true);
        assertEquals(theme.start(), theme.colorAt(0, 0, true));
        assertEquals(theme.end(), theme.colorAt(0.5, 0, true));
        assertEquals(theme.colorAt(0.25, 0, true), theme.colorAt(0.25, 4_000_000_000L, true));
        assertNotEquals(theme.colorAt(0, 0, true), theme.colorAt(0, 1_000_000_000L, true));
        assertEquals(theme.start(), theme.colorAt(0, 1_000_000_000L, false));
    }

    @Test void honorsSolidModeAndReducedMotion() {
        var solid = theme(false, true);
        assertEquals(solid.solid(), solid.colorAt(0.75, 1_000_000_000L, true));
        var still = theme(true, false);
        assertEquals(still.start(), still.colorAt(0, 3_000_000_000L, true));
        assertEquals(still.end(), still.colorAt(0.5, 3_000_000_000L, true));
    }

    @Test void choosesReadableTextForBrightAndDarkAccents() {
        assertEquals(0xFF10181C, theme(true, true).foreground());
        assertEquals(0xFFFFFFFF, new ThemeAccent.Snapshot(true, "Dark", true, 0xFF161F40, 0xFF311C49, 0, true).foreground());
    }

    @Test void writesOneCompleteSharedDocumentAndReplacesAtomically() throws Exception {
        Path target = directory.resolve("theme-accent.json");
        ThemeAccent.write(target, theme(true, true));
        var first = new JsonParser().parse(Files.readString(target)).getAsJsonObject();
        assertEquals(1, first.get("version").getAsInt());
        assertEquals("Arcadium", first.get("preset").getAsString());
        assertEquals("#3AA0FF", first.get("start").getAsString());
        assertEquals("#36F1B0", first.get("end").getAsString());
        assertTrue(first.get("gradient").getAsBoolean());
        assertFalse(first.get("custom").getAsBoolean());
        assertEquals(1f, first.get("speed").getAsFloat());
        assertEquals(100f, first.get("smoothness").getAsFloat());
        ThemeAccent.write(target, theme(false, false));
        var second = new JsonParser().parse(Files.readString(target)).getAsJsonObject();
        assertFalse(second.get("gradient").getAsBoolean());
        assertFalse(second.get("animate").getAsBoolean());
        try (var files = Files.list(directory)) { assertEquals(1L, files.count()); }
    }

    @Test void speedChangesThePeriodIncludingFractionalAndStoppedRates() {
        var normal = theme(true, true);
        var slow = new ThemeAccent.Snapshot(true, "Arcadium", false, normal.start(), normal.end(), normal.solid(), true, 0.5f, 100);
        var fast = new ThemeAccent.Snapshot(true, "Arcadium", false, normal.start(), normal.end(), normal.solid(), true, 2, 100);
        var stopped = new ThemeAccent.Snapshot(true, "Arcadium", false, normal.start(), normal.end(), normal.solid(), true, 0, 100);
        assertEquals(normal.colorAt(0, 1_000_000_000L, true), slow.colorAt(0, 2_000_000_000L, true));
        assertEquals(normal.colorAt(0, 2_000_000_000L, true), fast.colorAt(0, 1_000_000_000L, true));
        assertEquals(stopped.start(), stopped.colorAt(0, 120_000_000_000L, true));
        assertNotEquals(slow.colorAt(0, 0, true), slow.colorAt(0, 4_000_000_000L, true));
    }

    @Test void changingSpeedPausingAndResumingPreservesTheCurrentPosition() {
        var clock = new GradientClock(0, 1);
        assertEquals(0.25, clock.at(1_000_000_000L), 1e-9);
        clock.change(2, 1_000_000_000L, false);
        assertEquals(0.25, clock.at(1_000_000_000L), 1e-9);
        assertEquals(0.75, clock.at(2_000_000_000L), 1e-9);
        clock.change(0, 2_000_000_000L, false);
        assertEquals(0.75, clock.at(100_000_000_000L), 1e-9);
        clock.change(0.5f, 100_000_000_000L, false);
        assertEquals(0.75, clock.at(100_000_000_000L), 1e-9);
        assertEquals(0.875, clock.at(101_000_000_000L), 1e-9);
        clock.change(1, 101_000_000_000L, true);
        assertEquals(0, clock.at(105_000_000_000L), 1e-9);
    }

    @Test void smoothnessControlsBlendWidthAndRejectsNonfiniteSettings() {
        var soft = theme(true, true);
        var sharp = new ThemeAccent.Snapshot(true, "Arcadium", false, soft.start(), soft.end(), soft.solid(), true, 1, 0);
        assertEquals(sharp.start(), sharp.colorAt(0.125, 0, true));
        assertNotEquals(soft.start(), soft.colorAt(0.125, 0, true));
        assertEquals(sharp.end(), sharp.colorAt(0.375, 0, true));
        int softMiddle = soft.colorAt(0.25, 0, true), sharpMiddle = sharp.colorAt(0.25, 0, true);
        for (int shift : new int[]{0, 8, 16})
            assertTrue(Math.abs((softMiddle >> shift & 255) - (sharpMiddle >> shift & 255)) <= 1);
        var invalid = new ThemeAccent.Snapshot(true, "Arcadium", true, 0, 0, 0, true, Float.NaN, Float.POSITIVE_INFINITY);
        assertEquals(1f, invalid.speed()); assertEquals(100f, invalid.smoothness());
        var clamped = new ThemeAccent.Snapshot(true, "Arcadium", true, 0, 0, 0, true, -2, 200);
        assertEquals(0f, clamped.speed()); assertEquals(100f, clamped.smoothness());
    }

    private ThemeAccent.Snapshot theme(boolean gradient, boolean animate) {
        return new ThemeAccent.Snapshot(gradient, "Arcadium", false, 0xFF3AA0FF, 0xFF36F1B0, 0xFF2B4BFF, animate);
    }
}
