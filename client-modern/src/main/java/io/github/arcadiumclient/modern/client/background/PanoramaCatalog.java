// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.client.background;

import java.util.List;

public final class PanoramaCatalog {
    public static final String DEFAULT = "lakeside-morning";

    public record Entry(String id, String name) {
        public String face(int index) {
            if (index < 0 || index > 5) throw new IllegalArgumentException("Invalid cubemap face");
            return "arcadium:panoramas/" + id + "/" + index + ".jpg";
        }

        public String preview() { return "/assets/arcadium/panoramas/" + id + "/preview.jpg"; }
    }

    public static final List<Entry> ENTRIES = List.of(
            new Entry("aurora-village", "Aurora Village"),
            new Entry("kelp-forest", "Kelp Forest"),
            new Entry("glasswater", "Glasswater"),
            new Entry("ocean-monument", "Ocean Monument"),
            new Entry("woodland-halls", "Woodland Halls"),
            new Entry("lakeside-morning", "Lakeside Morning"),
            new Entry("canopy-sunrise", "Canopy Sunrise"),
            new Entry("duskfall", "Duskfall"),
            new Entry("crimson-depths", "Crimson Depths"),
            new Entry("soulfire-valley", "Soulfire Valley"),
            new Entry("emberwood", "Emberwood"),
            new Entry("ender-spires", "Ender Spires"),
            new Entry("end-city", "End City"),
            new Entry("ancient-foundry", "Ancient Foundry"),
            new Entry("warped-hollow", "Warped Hollow")
    );

    private PanoramaCatalog() { }

    public static Entry find(String id) {
        return ENTRIES.stream().filter(entry -> entry.id().equals(id)).findFirst()
                .orElseGet(() -> ENTRIES.stream().filter(entry -> entry.id().equals(DEFAULT)).findFirst().orElseThrow());
    }

    public static String resourcePackFace(int index) {
        if (index < 0 || index > 5) throw new IllegalArgumentException("Invalid cubemap face");
        return "minecraft:textures/gui/title/background/panorama_" + index + ".png";
    }

    public static float blurRadius(int percentage) { return Math.clamp(percentage, 0, 100) * .1f; }
}
