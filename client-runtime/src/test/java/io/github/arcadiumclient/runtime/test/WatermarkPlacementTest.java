// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.test;

import java.util.List;
import org.junit.jupiter.api.Test;
import io.github.arcadiumclient.runtime.client.gui.WatermarkPlacement;
import io.github.arcadiumclient.runtime.client.gui.WatermarkPlacement.Rect;

import static org.junit.jupiter.api.Assertions.*;

class WatermarkPlacementTest {
    @Test void keepsTheOriginalBottomRightBoundsWhenNoOverlayOccupiesThem() {
        assertEquals(new Rect(929, 624, 321, 66), WatermarkPlacement.choose(1280, 720, 321, 66, 30, List.of()));
        assertEquals(new Rect(203, 208, 107, 22), WatermarkPlacement.choose(320, 240, 107, 22, 10, List.of(new Rect(0, 0, 100, 20))));
    }

    @Test void avoidsTheActualJeiSidebarSearchAndCreativeTabsInTheReportedScreenshot() {
        List<Rect> occupied = List.of(new Rect(936, 0, 344, 720), new Rect(936, 654, 330, 66), new Rect(350, 72, 586, 564), new Rect(367, 654, 546, 66));
        Rect result = WatermarkPlacement.choose(1280, 720, 321, 66, 30, occupied);
        clear(result, occupied); assertEquals(624, result.y()); assertTrue(result.right() <= 350);
        assertEquals(321, result.width()); assertEquals(66, result.height()); inside(result, 1280, 720);
    }

    @Test void usesTheFreeTopAreaWhenNeiControlsOccupyBothLowerCorners() {
        List<Rect> occupied = List.of(new Rect(0, 0, 330, 720), new Rect(936, 0, 344, 720), new Rect(350, 72, 586, 564), new Rect(367, 654, 546, 66));
        Rect result = WatermarkPlacement.choose(1280, 720, 321, 66, 30, occupied);
        clear(result, occupied); assertTrue(result.bottom() <= 72); assertTrue(result.x() >= 330); inside(result, 1280, 720);
    }

    @Test void updatesAtDifferentFramebufferScalesWithoutRoundingIntoTheOverlay() {
        for (int[] viewport : new int[][] {{1280, 720}, {1920, 1080}, {854, 480}}) {
            int width = viewport[0], height = viewport[1];
            Rect panel = WatermarkPlacement.pixels(new Rect(312, 0, 115, 240), 427, 240, width, height);
            Rect menu = WatermarkPlacement.pixels(new Rect(117, 24, 195, 188), 427, 240, width, height);
            assertEquals(width, panel.right()); assertEquals(height, panel.bottom());
            Rect result = WatermarkPlacement.choose(width, height, width / 4, Math.max(1, height / 11), Math.max(1, width / 43), List.of(panel, menu));
            clear(result, List.of(panel, menu)); inside(result, width, height);
        }
    }

    @Test void clipsOffscreenAndEmptyWidgetBoundsBeforePlacement() {
        assertEquals(new Rect(0, 0, 30, 45), WatermarkPlacement.pixels(new Rect(-10, -10, 20, 25), 100, 100, 300, 300));
        Rect empty = WatermarkPlacement.pixels(new Rect(200, 200, 20, 20), 100, 100, 300, 300);
        assertEquals(0, empty.width()); assertEquals(0, empty.height());
        assertEquals(new Rect(183, 268, 107, 22), WatermarkPlacement.choose(300, 300, 107, 22, 10, List.of(empty)));
    }

    @Test void keepsTheEntireLogoVisibleInsideSmallViewports() {
        Rect result = WatermarkPlacement.choose(80, 20, 321, 66, 30, List.of());
        inside(result, 80, 20); assertTrue(result.width() > 0); assertTrue(result.height() > 0);
        assertTrue(Math.abs((long) result.width() * 66 - (long) result.height() * 321) <= 321 + 66);
    }

    @Test void neverHidesTheLogoWhenTheWholeScreenIsOccupied() {
        Rect result = WatermarkPlacement.choose(640, 480, 214, 44, 20, List.of(new Rect(0, 0, 640, 480)));
        inside(result, 640, 480); assertEquals(214, result.width()); assertEquals(44, result.height());
    }

    @Test void changesBackToBottomRightWhenTheOverlayIsClosed() {
        List<Rect> occupied = List.of(new Rect(300, 0, 127, 240));
        Rect moved = WatermarkPlacement.choose(427, 240, 107, 22, 10, occupied);
        clear(moved, occupied);
        assertNotEquals(moved, WatermarkPlacement.choose(427, 240, 107, 22, 10, List.of()));
        assertEquals(new Rect(310, 208, 107, 22), WatermarkPlacement.choose(427, 240, 107, 22, 10, List.of()));
    }

    @Test void fitsTheLogoBetweenNeiRecipeToolbarAndPanelWithoutCoveringTimeAndSaveControls() {
        List<Rect> occupied = List.of(new Rect(0, 0, 252, 168), new Rect(0, 177, 180, 453),
            new Rect(270, 0, 630, 60), new Rect(910, 0, 370, 720), new Rect(376, 112, 526, 491),
            new Rect(0, 654, 240, 66), new Rect(363, 654, 550, 66));
        Rect result = WatermarkPlacement.choose(1280, 720, 321, 66, 30, occupied);
        clear(result, occupied); inside(result, 1280, 720);
        assertTrue(result.height() >= 22 && result.height() < 66);
        assertTrue(Math.abs((long) result.width() * 66 - (long) result.height() * 321) <= 66);
        assertEquals(new Rect(929, 624, 321, 66), WatermarkPlacement.choose(1280, 720, 321, 66, 30, List.of()));
    }

    @Test void searchesCombinedInteriorEdgesBeforeReducingTheLogo() {
        List<Rect> occupied = List.of(new Rect(0, 0, 640, 100), new Rect(0, 260, 640, 220),
            new Rect(0, 0, 100, 480), new Rect(330, 0, 310, 480));
        Rect result = WatermarkPlacement.choose(640, 480, 214, 44, 20, occupied);
        clear(result, occupied); inside(result, 640, 480);
        assertEquals(214, result.width()); assertEquals(44, result.height());
        assertTrue(result.x() > 100 && result.right() < 330 && result.y() > 100 && result.bottom() < 260);
    }

    @Test void preservesTheReadableMinimumWhenThePanelHasNoUsableSpace() {
        Rect result = WatermarkPlacement.choose(427, 240, 107, 22, 10, List.of(new Rect(0, 0, 427, 240)));
        assertEquals(107, result.width()); assertEquals(22, result.height()); inside(result, 427, 240);
    }

    private static void clear(Rect result, List<Rect> obstacles) { for (Rect obstacle : obstacles) assertFalse(result.overlaps(obstacle), result + " overlaps " + obstacle); }
    private static void inside(Rect result, int width, int height) { assertTrue(result.x() >= 0 && result.y() >= 0 && result.right() <= width && result.bottom() <= height); }
}
