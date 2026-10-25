// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.migration;

import org.polyfrost.oneconfig.api.config.v1.Tree;
import org.polyfrost.oneconfig.api.config.v1.annotations.*;

public final class LegacyFullbrightConfig extends LegacyNativeConfig {
    public static final LegacyFullbrightConfig INSTANCE = new LegacyFullbrightConfig();
    @Switch(title = "Enabled") public static boolean enable = true;
    @Slider(title = "Light level", description = "Interpolates between the configured normal and full brightness gamma values.", min = 0, max = 15, step = 1) public static int lightLevel = 15;
    @Include public static String gammaNativeState = "";
    private LegacyFullbrightConfig() { super("fullbright.json", "FullBright", Category.VISUALS); }
    @Override protected void onInitialized(Tree tree) {
        tree.addMetadata("mod_card_toggle", "enable");
        hideIf("gammaNativeState", () -> true);
        GammaProfiles.loadLegacy(hadLegacyDocument());
        addCallback("enable", value -> { GammaProfiles.applyLegacy(); return false; });
        addCallback("lightLevel", value -> { GammaProfiles.applyLegacy(); return false; });
        if (!hadLegacyDocument()) save();
    }
    @Button(title = "Advanced gamma settings", text = "Open") public void nativeOptions() { GammaProfiles.openNativeOptions(); }
}
