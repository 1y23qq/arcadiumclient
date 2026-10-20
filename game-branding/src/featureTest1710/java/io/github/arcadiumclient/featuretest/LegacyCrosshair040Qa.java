// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.LegacyComposeQa.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;

public final class LegacyCrosshair040Qa {
    private static final String FEATURES = "io.github.arcadiumclient.runtime.client.features.";
    private static final List<Map<String, Object>> CASES = new ArrayList<>();
    private static Object tree, previousDesign;
    private static boolean previousEnabled, begun, complete, advancePending;
    private static int index, entered, customBefore, vanillaBefore, vanilla;

    private LegacyCrosshair040Qa() {}

    public static void vanillaDraw() { if (Boolean.getBoolean("arcadium.featureTest040") && begun && !complete) vanilla++; }

    static boolean tick(Minecraft client, Map<String, Object> result, int tick) throws Exception {
        if (complete) return true;
        if (!begun) {
            begun = true; result.put("nativeThirdPersonCrosshair", CASES);
            tree = call(instance("org.polyfrost.oneconfig.internal.ui.api.ConfigRegistry"), "findTree", "crosshairtweaks.json");
            require(tree != null && call(tree, "getProp", "arcadiumThirdPersonCrosshair") != null, "The native third-person setting was not attached");
            previousDesign = stat(FEATURES + "CustomCrosshair", "design");
            previousEnabled = (boolean) stat(FEATURES + "CustomCrosshair", "isActive");
            Object design = Class.forName(FEATURES + "CrosshairDesign").getConstructor().newInstance();
            design.getClass().getField("mode").setInt(design, 1);
            int[] pixels = (int[]) design.getClass().getField("pixels").get(design);
            for (int point = 10; point <= 21; point++) { pixels[16 * 32 + point] = 0xFFFFFFFF; pixels[point * 32 + 16] = 0xFFFFFFFF; }
            call(instance(FEATURES + "CustomCrosshair"), "update", design);
            set("disableCrosshair", false); set("crosshairOpacityFirstPerson", 100f); set("crosshairOpacityThirdPerson", 100f);
            client.openScreen(null); client.options.debugEnabled = false; client.options.hideGui = false;
            select(client, tick);
            return false;
        }
        if (advancePending) {
            advancePending = false;
            if (index < 8) select(client, tick);
            else {
                set("arcadiumThirdPersonCrosshair", true); set("disableCrosshair", true);
                call(instance(FEATURES + "CustomCrosshair"), "setEnabled", true);
                customBefore = count(); vanillaBefore = vanilla; entered = tick;
            }
            return false;
        }
        if (tick - entered < 12) return false;
        int custom = count() - customBefore, nativeDraws = vanilla - vanillaBefore;
        if (index < 8) {
            boolean show = index % 2 == 0, canvas = index < 4;
            require(((canvas ? custom : nativeDraws) > 0) == show, "Native crosshair case " + index + " drew canvas=" + custom + ", vanilla=" + nativeDraws);
            if (canvas) require(nativeDraws == 0, "The native vanilla crosshair was drawn over its Canvas replacement");
            CASES.add(Map.of("canvas", canvas, "perspective", client.options.perspective, "show", show, "canvasDraws", custom, "vanillaDraws", nativeDraws));
            if (show) Legacy040Qa.requestCapture("040-1710-crosshair-" + index + ".png");
            index++;
            advancePending = true;
            return false;
        }
        require(custom == 0 && nativeDraws == 0, "The native Disable Crosshair control did not suppress actual rendering");
        set("disableCrosshair", false); set("arcadiumThirdPersonCrosshair", false);
        call(instance(FEATURES + "CustomCrosshair"), "update", previousDesign);
        call(instance(FEATURES + "CustomCrosshair"), "setEnabled", previousEnabled);
        client.options.perspective = 0;
        result.put("nativeDisableCrosshairControl", true);
        complete = true;
        return true;
    }

    private static void select(Minecraft client, int tick) throws Exception {
        client.options.perspective = index / 2 % 2 + 1;
        set("arcadiumThirdPersonCrosshair", index % 2 == 0);
        call(instance(FEATURES + "CustomCrosshair"), "setEnabled", index < 4);
        customBefore = count(); vanillaBefore = vanilla; entered = tick;
    }

    private static void set(String name, Object value) throws Exception { call(call(tree, "getProp", name), "setAsReferential", value); }
    private static int count() throws Exception { return ((Number) stat(FEATURES + "CustomCrosshairRenderer", "renderedCount")).intValue(); }
}
