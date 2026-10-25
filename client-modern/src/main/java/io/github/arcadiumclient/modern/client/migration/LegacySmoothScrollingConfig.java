// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.migration;

import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.Tree;
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;

public final class LegacySmoothScrollingConfig extends Config {
    public static final LegacySmoothScrollingConfig INSTANCE = new LegacySmoothScrollingConfig();
    @Slider(title = "Scroll strength", min = .1f, max = 2.5f, step = .05f) public static float scrollStrength = .5f;
    @Slider(title = "Scrollbar friction", min = .01f, max = .1f, step = .001f) public static float scrollbarFriction = .025f;
    @Switch(title = "Push back") public static boolean pushBack = true;
    @Slider(title = "Push back strength", min = .1f, max = 2, step = .05f) public static float pushBackStrength = 1;
    @Slider(title = "Animation duration", min = .1f, max = 5, step = .05f) public static float animationDuration = 1;
    private LegacySmoothScrollingConfig() { super("smoothscrollingrefurbished.json", "/assets/arcadium/logo.svg", "Smooth Scrolling", Category.QOL); }
    @Override protected void onInitialized(Tree tree) {
        addDependency("pushBackStrength", "pushBack");
        tree.onAllProps((path, property) -> property.addCallback(value -> { ModernLegacyNativeProfiles.applyScrolling(); return false; }));
        ModernLegacyNativeProfiles.applyScrolling();
    }
}
