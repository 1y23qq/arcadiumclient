// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.LegacyComposeQa.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;

final class LegacyVideo040Qa {
    private static boolean complete;
    private LegacyVideo040Qa() {}

    static void verify(Minecraft client, Map<String, Object> result) throws Exception {
        if (complete) return;
        require(client.screen != null && client.screen.getClass().getSimpleName().equals("ArcadiumVideoSettingsScreen"),
            "The native video model test must run on its real Arcadium screen");
        Object model = field(client.screen, "model");
        Map<String, Object> options = new LinkedHashMap<>();
        for (Object option : (List<?>) call(model, "getOptions")) options.put((String) call(option, "getKey"), option);
        for (String absent : List.of("entity_shadows", "alternate_blocks", "vbo", "dynamic_lights"))
            require(!options.containsKey(absent), "The native video screen exposes an unsupported control: " + absent);
        require(options.containsKey("advanced_opengl"), "The native occlusion control is missing");
        Object layout = instance("io.github.arcadiumclient.runtime.client.gui.VideoSettingsLayout");
        int mainControls = 0;
        for (Object pair : (List<?>) call(layout, "getMainRows")) for (String side : List.of("getFirst", "getSecond")) {
            String key = (String) call(pair, side);
            require(options.containsKey(key), "The native main page has an empty/unsupported control slot: " + key);
            mainControls++;
        }
        require(mainControls == 10, "The native main page has an unexpected layout");
        Map<String, Object> prior = new LinkedHashMap<>();
        for (String key : List.of("clouds", "particles", "brightness", "dynamic_fov")) prior.put(key, call(options.get(key), "getValue"));
        Map<String, Object> wanted = new LinkedHashMap<>();
        wanted.put("clouds", !(boolean) prior.get("clouds"));
        wanted.put("particles", (((Number) prior.get("particles")).intValue() + 1) % 3);
        wanted.put("brightness", ((Number) prior.get("brightness")).intValue() == 75 ? 25 : 75);
        wanted.put("dynamic_fov", !(boolean) prior.get("dynamic_fov"));
        try {
            for (Map.Entry<String, Object> value : wanted.entrySet()) call(options.get(value.getKey()), "change", value.getValue());
            require(client.options.renderClouds == (boolean) prior.get("clouds")
                && client.options.particles == ((Number) prior.get("particles")).intValue(),
                "Staged native video controls changed game state before Apply");
            call(model, "undo");
            for (Map.Entry<String, Object> value : prior.entrySet())
                require(value.getValue().equals(call(options.get(value.getKey()), "getValue")), "Undo did not restore " + value.getKey());
            for (Map.Entry<String, Object> value : wanted.entrySet()) call(options.get(value.getKey()), "change", value.getValue());
            call(model, "apply");
            require(client.options.renderClouds == (boolean) wanted.get("clouds"), "Clouds did not write the native boolean");
            require(client.options.particles == ((Number) wanted.get("particles")).intValue(), "Particles did not write the native mode");
            require(Math.abs(client.options.gamma * 100 - ((Number) wanted.get("brightness")).intValue()) < 0.01,
                "Brightness did not write the native gamma scale");
            require(stat("io.github.arcadiumclient.runtime.client.ClientPreferences", "getDynamicFov").equals(wanted.get("dynamic_fov")),
                "Dynamic FOV did not save the native movement-FOV preference");
            require(!(boolean) call(model, "getChanged"), "Apply did not commit the native model values");
            result.put("nativeVideoSettings", Map.of("supportedOptionKeys", List.copyOf(options.keySet()), "mainControls", mainControls,
                "stagedUndoApply", true, "nativeCloudsParticlesBrightnessAndDynamicFov", true));
            complete = true;
        } finally {
            for (Map.Entry<String, Object> value : prior.entrySet()) call(options.get(value.getKey()), "change", value.getValue());
            call(model, "apply");
        }
    }
}
