// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.LegacyComposeQa.*;

import io.github.arcadiumclient.featuretest.mixin.LegacyScreenInput;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Mouse;

final class LegacyResourceDrag040Qa {
    private static int age;
    private static double startX, startY, targetX, targetY;
    private LegacyResourceDrag040Qa() {}

    static boolean tick(Minecraft client, Map<String, Object> result) throws Exception {
        if (++age == 1) {
            Object button = call(description(client.screen, "Enable pack"), "getBoundsInRoot");
            double width = number(button, "getRight") - number(button, "getLeft");
            double height = number(button, "getBottom") - number(button, "getTop");
            require(width >= 35 && height >= 35, "The native pack action target is too small: " + width + "x" + height);
            result.put("nativePackActionSize", List.of(width, height));
            Object card = null;
            for (Object node : nodes(client.screen)) {
                if (properties(node).get("ContentDescription") instanceof Collection<?> labels
                    && labels.stream().anyMatch(value -> value.toString().startsWith("Resource pack ") && value.toString().contains("Native Needle"))) { card = node; break; }
            }
            require(card != null, "The filtered native pack card has no pointer target");
            Object source = call(card, "getBoundsInRoot"), destination = field(client.screen, "activeBounds");
            startX = number(source, "getLeft") + 60; startY = (number(source, "getTop") + number(source, "getBottom")) / 2;
            targetX = (number(destination, "getLeft") + number(destination, "getRight")) / 2;
            targetY = (number(destination, "getTop") + number(destination, "getBottom")) / 2;
            int[] position = cursor(client, startX, startY);
            ((LegacyScreenInput) client.screen).arcadium$press(position[0], position[1], 0);
            return false;
        }
        double fraction = Math.max(0, Math.min(1, (age - 3) / 12.));
        int[] position = cursor(client, startX + (targetX - startX) * fraction, startY + (targetY - startY) * fraction);
        if (age > 3 && age < 17) ((LegacyScreenInput) client.screen).arcadium$drag(position[0], position[1], 0, age * 50L);
        if (age == 17) ((LegacyScreenInput) client.screen).arcadium$release(position[0], position[1], 0);
        if (age < 20) return false;
        List<?> active = (List<?>) call(client.screen, "getActive");
        require(active.size() == 1 && active.get(0).toString().contains("Native Needle"), "The real native pointer drag did not move the filtered folder pack into Active: " + active);
        result.put("nativeFilteredPackPointerDrag", true);
        return true;
    }

    private static double number(Object target, String name) throws Exception { return ((Number) call(target, name)).doubleValue(); }

    private static int[] cursor(Minecraft client, double x, double y) throws Exception {
        Object platform = stat("org.polyfrost.oneconfig.api.platform.v1.Platform", "screen");
        double ratio = number(platform, "pixelRatio");
        int viewport = ((Number) call(platform, "viewportHeight")).intValue();
        Mouse.setCursorPosition((int) Math.round(x * ratio), viewport - (int) Math.round(y * ratio) - 1);
        return new int[] {(int) Math.round(x * client.screen.width / (client.width / ratio)), (int) Math.round(y * client.screen.height / (client.height / ratio))};
    }
}
