// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.LegacyComposeQa.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.vertex.Tesselator;
import org.lwjgl.opengl.GL11;

final class LegacyHitbox040Qa {
    private static boolean complete;
    private LegacyHitbox040Qa() {}

    static void verify(Minecraft client, Map<String, Object> result) throws Exception {
        if (complete) return;
        Object module = instance("org.polyfrost.polyhitbox.config.ModConfig");
        Object renderer = instance("org.polyfrost.polyhitbox.render.HitboxRenderer");
        Object buffer = instance("org.polyfrost.polyhitbox.legacy.NativeVertices");
        Object config = Class.forName("org.polyfrost.polyhitbox.config.HitboxConfig").getConstructor().newInstance();
        call(config, "setShowSide", true); call(config, "setShowOutline", false);
        call(config, "setShowEyeHeight", false); call(config, "setShowViewRay", false);
        call(config, "resolveColors");
        Map<String, Object> previous = new LinkedHashMap<>();
        for (String name : List.of("camX", "camY", "camZ", "partialTicks", "clamping")) previous.put(name, field(renderer, name));
        boolean accurate = (boolean) field(module, "accurateHitboxes");
        double priorX = client.player.prevX, priorY = client.player.prevY, priorZ = client.player.prevZ;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        try {
            call(module, "setAccurateHitboxes", true);
            require(!(boolean) call(module, "getAccurateHitboxes"), "Arcadium did not disable the expanded picking-margin hitbox");
            set(renderer, "camX", client.player.x); set(renderer, "camY", client.player.y); set(renderer, "camZ", client.player.z);
            set(renderer, "partialTicks", 1f);
            int[] normal = draw(renderer, buffer, client, config);
            double[] box = {
                client.player.shape.minX - client.player.x, client.player.shape.minY - client.player.y, client.player.shape.minZ - client.player.z,
                client.player.shape.maxX - client.player.x, client.player.shape.maxY - client.player.y, client.player.shape.maxZ - client.player.z};
            double[] actual = bounds(normal);
            assertBox(box, actual, "The native hitbox renderer expanded or misplaced the vanilla AABB");
            client.player.prevX = client.player.x + 0.8;
            client.player.prevY = client.player.y + 0.4;
            client.player.prevZ = client.player.z - 0.6;
            set(renderer, "partialTicks", 0.25f);
            double[] interpolated = bounds(draw(renderer, buffer, client, config));
            double[] expected = box.clone();
            for (int index = 0; index < 6; index++) expected[index] += switch(index % 3) { case 0 -> 0.6; case 1 -> 0.3; default -> -0.45; };
            assertBox(expected, interpolated, "The native hitbox did not use the actual partial tick");
            result.put("nativeHitboxRendering", Map.of("vanillaBounds", box, "emittedBounds", actual,
                "sideQuadVertexCount", normal.length / 8, "interpolatedBounds", interpolated,
                "storedExpandedFlagIgnored", true));
            complete = true;
        } finally {
            client.player.prevX = priorX; client.player.prevY = priorY; client.player.prevZ = priorZ;
            call(module, "setAccurateHitboxes", accurate);
            for (Map.Entry<String, Object> entry : previous.entrySet()) set(renderer, entry.getKey(), entry.getValue());
            GL11.glPopClientAttrib(); GL11.glPopAttrib();
        }
    }

    private static int[] draw(Object renderer, Object buffer, Minecraft client, Object config) throws Exception {
        call(buffer, "begin", GL11.GL_QUADS);
        try {
            call(renderer, "drawEntity", buffer, client.player, config);
            Object state = Tesselator.INSTANCE.buildState(0f, 0f, 0f);
            for (Field field : state.getClass().getDeclaredFields()) if (field.getType() == int[].class) {
                field.setAccessible(true); int[] data = ((int[]) field.get(state)).clone();
                require(data.length == 24 * 8, "The native AABB did not render six complete side quads");
                return data;
            }
            throw new IllegalStateException("Native hitbox vertex data not found");
        } finally { call(buffer, "finish"); }
    }

    private static double[] bounds(int[] data) {
        double[] box = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (int vertex = 0; vertex < data.length / 8; vertex++) for (int axis = 0; axis < 3; axis++) {
            double value = Float.intBitsToFloat(data[vertex * 8 + axis]);
            box[axis] = Math.min(box[axis], value); box[axis + 3] = Math.max(box[axis + 3], value);
        }
        return box;
    }

    private static void assertBox(double[] expected, double[] actual, String error) {
        for (int index = 0; index < 6; index++) require(Math.abs(expected[index] - actual[index]) < 0.00001, error);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true);
        field.set(Modifier.isStatic(field.getModifiers()) ? null : target, value);
    }
}
