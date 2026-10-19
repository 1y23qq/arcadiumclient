// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features;

import java.util.UUID;

public final class VisualFeaturesPureTest {
    public static void main(String[] args) throws Exception {
        crosshairGeometry();
        canvasProfiles();
        deathConfirmation();
        skinConfigAdapter();
        System.out.println("Visual feature checks passed: geometry, canvas, death confirmation, skin config adapter");
    }

    private static void crosshairGeometry() {
        CrosshairDesign design = new CrosshairDesign();
        design.outline = false;
        CrosshairDesign.Mesh mesh = design.mesh();
        check(mesh.quads().size() == 4, "Cross has four arms");
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (CrosshairDesign.Quad q : mesh.quads()) {
            minX = Math.min(minX, Math.min(q.x1(), q.x3()));
            maxX = Math.max(maxX, Math.max(q.x1(), q.x3()));
            minY = Math.min(minY, Math.min(q.y1(), q.y3()));
            maxY = Math.max(maxY, Math.max(q.y1(), q.y3()));
        }
        check(minX == -maxX && minY == -maxY, "Shape is centered independently of screen dimensions");
        check(mesh.quads().get(0).y3() - mesh.quads().get(0).y1() == 1, "One pixel thickness remains exact");
        design.mode = 1;
        check(design.mesh().isEmpty(), "An empty canvas allows vanilla fallback");
        design.mode = 0;
        design.shape = 2;
        check(design.mesh().quads().size() == 64, "Ring uses bounded cached geometry");
        design.length = Integer.MAX_VALUE;
        design.thickness = -1;
        design.scale = Float.NaN;
        design.opacity = Float.POSITIVE_INFINITY;
        design.normalize();
        check(design.length == 20 && design.thickness == 1 && design.scale == 1 && design.opacity == 1, "Corrupt profile values are bounded");
    }

    private static void canvasProfiles() {
        CrosshairDesign design = new CrosshairDesign();
        design.mode = 1;
        design.outline = false;
        design.paint(15, 15, 0xFFFF0000, true, true);
        check(design.pixels[15 * 32 + 15] == 0xFFFF0000 && design.pixels[16 * 32 + 16] == 0xFFFF0000,
                "Mirroring uses the true center between even canvas pixels");
        CrosshairDesign copy = design.copy();
        copy.paint(15, 15, 0, true, true);
        check(!design.mesh().isEmpty() && copy.mesh().isEmpty(), "Profiles own their pixel data");
        design.resizeCanvas(64);
        check(design.pixels[31 * 64 + 31] == 0xFFFF0000 && design.pixels[32 * 64 + 32] == 0xFFFF0000,
                "Canvas resizing preserves the crosshair center");
        design.resizeCanvas(16);
        check(design.pixels[7 * 16 + 7] == 0xFFFF0000 && design.pixels[8 * 16 + 8] == 0xFFFF0000,
                "Canvas cropping preserves the crosshair center");
        check(design.mesh().quads().size() == 2, "Adjacent same-color pixels are merged into runs");
        design.outline = true;
        check(design.mesh().quads().size() == 6, "Pixel canvas outline is generated and merged separately");
    }

    private static void deathConfirmation() {
        KillEffectTracker tracker = new KillEffectTracker();
        UUID first = new UUID(1, 1), reused = new UUID(2, 2);
        tracker.hit(10, first, 1);
        check(!tracker.confirm(10, reused, 2, 100), "Reused entity IDs do not credit a different entity");
        tracker.hit(10, first, 1);
        check(tracker.confirm(10, first, 100, 100), "Recent attack and matching death confirm once");
        check(!tracker.confirm(10, first, 101, 100), "Repeated death events do not repeat effects");
        tracker.hit(10, first, 1);
        check(!tracker.confirm(10, first, 102, 100), "Expired attacks do not credit kills");
        for (int i = 0; i < 100; i++) tracker.hit(i, first, i);
        check(tracker.size() == 32, "Tracking remains bounded during large fights");
        tracker.expire(1000, 100);
        check(tracker.size() == 0, "Unconfirmed targets expire without generating effects");
        tracker.hit(10, first, 20);
        tracker.clear();
        check(!tracker.confirm(10, first, 21, 100), "World resets discard prior targets");
    }

    public static final class PlainConfig {
        private boolean enableHat;
        private int renderDistanceLOD;
        public boolean getEnableHat() { return enableHat; }
        public void setEnableHat(boolean value) { enableHat = value; }
        public int getRenderDistanceLOD() { return renderDistanceLOD; }
        public void setRenderDistanceLOD(int value) { renderDistanceLOD = value; }
    }

    public static final class Option {
        private Object value = false;
        public Object get() { return value; }
        public void set(Object value) { this.value = value; }
    }

    public static final class OslConfig {
        private final Option enableHat = new Option();
        public Option getEnableHat() { return enableHat; }
    }

    public static final class FieldConfig {
        public boolean enableHat;
    }

    private static void skinConfigAdapter() throws Exception {
        PlainConfig plain = new PlainConfig();
        SkinLayersBridge.write(plain, "enableHat", true);
        SkinLayersBridge.write(plain, "renderDistanceLOD", 24);
        check(Boolean.TRUE.equals(SkinLayersBridge.read(plain, "enableHat")), "Bundled plain Kotlin properties can be enabled");
        check(Integer.valueOf(24).equals(SkinLayersBridge.read(plain, "renderDistanceLOD")), "Bundled distance property is applied");
        SkinLayersBridge.write(plain, "enableHat", false);
        check(Boolean.FALSE.equals(SkinLayersBridge.read(plain, "enableHat")), "Bundled renderer can be disabled");
        OslConfig osl = new OslConfig();
        SkinLayersBridge.write(osl, "enableHat", true);
        check(Boolean.TRUE.equals(SkinLayersBridge.read(osl, "enableHat")), "OSL option compatibility remains supported");
        FieldConfig modern = new FieldConfig();
        SkinLayersBridge.write(modern, "enableHat", true);
        check(Boolean.TRUE.equals(SkinLayersBridge.read(modern, "enableHat")), "Modern public-field config remains supported");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
