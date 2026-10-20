// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.
package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.LegacyComposeQa.*;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;

final class LegacyFeet040Qa {
    private LegacyFeet040Qa() {}

    static void verify(Minecraft client, Map<String, Object> result) throws Exception {
        require(client.player != null, "The native feet test requires a real player");
        var player = client.player;
        double[] prior = {player.x, player.shape.minY, player.z, player.y, player.lastX, player.lastY, player.lastZ, player.prevX, player.prevY, player.prevZ};
        float yaw = player.yaw, pitch = player.pitch, lastYaw = player.lastYaw, lastPitch = player.lastPitch;
        Object height = null, position = null;
        for (Object hud : (Collection<?>) call(instance("org.polyfrost.oneconfig.api.hud.v1.HudManager"), "providers")) {
            if (hud.getClass().getSimpleName().equals("HeightLimitHud")) height = hud;
            if (hud.getClass().getName().equals("org.polyfrost.evergreenhud.client.hud.PositionHud")) position = hud;
        }
        require(height != null && position != null, "The real native height and position HUD providers are missing");
        Class<?> limits = Class.forName("io.github.arcadiumclient.runtime.legacy.LegacyHeightConfig");
        boolean debug = (boolean) call(limits, "getDebugModeEnabled");
        int custom = ((Number) call(limits, "getDebugModeHeight")).intValue();
        Class<?> cuteConfig = Class.forName("io.github.arcadiumclient.runtime.client.features.CuteVisualsConfig");
        Object cuteState = field(Class.forName("io.github.arcadiumclient.runtime.client.features.CuteVisuals"), "STATE");
        Map<String, Object> cuteSettings = new LinkedHashMap<>();
        for (String name : List.of("Enabled", "Hearts", "Dots", "OnlyWhileMoving")) cuteSettings.put(name, call(cuteConfig, "get" + name));
        Map<String, Object> hudSettings = new LinkedHashMap<>(), positionSettings = new LinkedHashMap<>();
        for (String name : List.of("Style", "ShowYLimit", "ShowCurrentY", "ShowDistanceToLimit")) hudSettings.put(name, call(height, "get" + name));
        for (String name : List.of("ShowX", "ShowY", "ShowZ", "ShowYaw", "ShowPitch", "ShowDirection", "Accuracy", "TrailingZeros")) positionSettings.put(name, call(position, "get" + name));
        try {
            player.setPositionAndAngles(prior[0], 8.5, prior[2], yaw, pitch);
            require(player.shape.minY == 8.5 && Math.floor(player.y) != 8, "The fixture did not distinguish feet from native eye coordinates");
            call(limits, "setDebugModeEnabled", false);
            call(height, "setStyle", 0);
            String classic = (String) call(height, "getText");
            require(classic.equals("8 / 256"), "Height Limit HUD used eye height instead of actual feet: " + classic);
            call(height, "setStyle", 1); call(height, "setShowYLimit", true);
            call(height, "setShowCurrentY", true); call(height, "setShowDistanceToLimit", true);
            String compact = (String) call(height, "getText");
            require(compact.equals("8 / 256 (248)"), "Compact height distance did not use actual feet: " + compact);
            call(limits, "setDebugModeEnabled", true); call(limits, "setDebugModeHeight", 100);
            String hundred = (String) call(height, "getText");
            require(hundred.equals("8 / 100 (92)"), "The hundred-block limit did not calculate distance from feet: " + hundred);
            Object center = Class.forName("io.github.arcadiumclient.runtime.legacy.BlockPos").getConstructor(Entity.class).newInstance(player);
            require(((Number) call(center, "getY")).intValue() == 8, "The actual light-level scan adapter centered on eye height");
            for (String name : List.of("ShowX", "ShowZ", "ShowYaw", "ShowPitch", "ShowDirection")) call(position, "set" + name, false);
            call(position, "setShowY", true); call(position, "setAccuracy", 2); call(position, "setTrailingZeros", true);
            call(position, "update"); call(position, "update");
            String coordinates = (String) call(position, "getText");
            require(((Number) field(position, "py")).doubleValue() == 8.5 && coordinates.contains("8.50") && !coordinates.contains("10.12"), "The actual Position HUD did not display feet height: " + coordinates);
            call(cuteConfig, "setEnabled", true); call(cuteConfig, "setHearts", true); call(cuteConfig, "setDots", true); call(cuteConfig, "setOnlyWhileMoving", false);
            call(cuteState, "clear"); stat("io.github.arcadiumclient.runtime.client.features.CuteVisuals", "tick");
            Map<String, Double> anchors = new LinkedHashMap<>();
            for (Object sprite : (Object[]) call(cuteState, "sprites")) {
                Object kind = field(sprite, "kind");
                if (kind == null) continue;
                String name = kind.toString();
                if (!name.equals("HEART") && !name.equals("SPARK")) continue;
                double emittedY = ((Number) field(sprite, "y")).doubleValue();
                require(emittedY >= 8.7 && emittedY <= 9.8, "Cute Visuals spawned relative to native eye height: " + emittedY);
                anchors.put(name, emittedY);
            }
            require(anchors.size() == 2, "The actual Cute Visuals heart and spark generators were not both exercised");
            result.put("nativeCuteVisualFeetAnchors", anchors);
            result.put("nativeFeetCoordinates", Map.of("feetY", player.shape.minY, "nativeEntityEyeY", player.y,
                "classicHeightHud", classic, "compactHeightHud", compact, "hundredBlockHeightHud", hundred,
                "positionHud", coordinates, "lightGridCenterY", call(center, "getY"), "cameraInterpolationUnchanged", true));
        } finally {
            player.setPositionAndAngles(prior[0], prior[1], prior[2], yaw, pitch);
            player.y = prior[3]; player.lastX = prior[4]; player.lastY = prior[5]; player.lastZ = prior[6];
            player.prevX = prior[7]; player.prevY = prior[8]; player.prevZ = prior[9]; player.lastYaw = lastYaw; player.lastPitch = lastPitch;
            call(limits, "setDebugModeEnabled", debug); call(limits, "setDebugModeHeight", custom);
            for (Map.Entry<String, Object> entry : hudSettings.entrySet()) call(height, "set" + entry.getKey(), entry.getValue());
            for (Map.Entry<String, Object> entry : positionSettings.entrySet()) call(position, "set" + entry.getKey(), entry.getValue());
            call(position, "update"); call(position, "update");
            call(cuteState, "clear");
            for (Map.Entry<String, Object> entry : cuteSettings.entrySet()) call(cuteConfig, "set" + entry.getKey(), entry.getValue());
        }
    }
}
