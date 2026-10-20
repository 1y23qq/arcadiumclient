// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.featuretest;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.Box;

public final class HitboxBounds038Qa {
    private static final String CONFIG = "org.polyfrost.polyhitbox.config.ModConfig";
    private static final String RENDERER = "org.polyfrost.polyhitbox.render.HitboxRenderer";
    private static final String[] RENDER_STATE = {
        "partialTicks", "camX", "camY", "camZ", "fwdX", "fwdY", "fwdZ", "ribbonScale", "clamping",
        "gateMinX", "gateMinY", "gateMinZ", "gateMaxX", "gateMaxY", "gateMaxZ",
        "clampMinX", "clampMinY", "clampMinZ", "clampMaxX", "clampMaxY", "clampMaxZ"
    };

    private HitboxBounds038Qa() { }

    public static void verify(Minecraft mc, Map<String, Object> checks) throws Exception {
        require(Boolean.getBoolean("arcadium.forgeQa") && "ArcadiumTest".equals(mc.getSession().getUsername())
                && "0".equals(mc.getSession().getAccessToken()), "Hitbox QA requires the isolated offline fixture");
        require(mc.isSingleplayer() && mc.world != null, "Hitbox QA requires the isolated local world");
        Class<?> configType = Class.forName(CONFIG);
        Object config = configType.getField("INSTANCE").get(null);
        Field margin = field(configType, "accurateHitboxes");
        Object previousMargin = margin.get(null);
        Class<?> rendererType = Class.forName(RENDERER);
        Object renderer = rendererType.getField("INSTANCE").get(null);
        Map<Field, Object> previousState = new LinkedHashMap<>();
        for (String name : RENDER_STATE) {
            Field field = field(rendererType, name);
            previousState.put(field, field.get(null));
        }
        List<Map<String, Object>> samples = new ArrayList<>();
        try {
            Object tree = configType.getMethod("getTree").invoke(config);
            Object property = tree.getClass().getMethod("getProp", String.class).invoke(tree, "accurateHitboxes");
            require(property != null && !(Boolean) property.getClass().getMethod("canDisplay").invoke(property),
                    "The obsolete expanded-bounds option remains visible");

            Class<?> visualType = Class.forName("org.polyfrost.polyhitbox.config.HitboxConfig");
            Object visual = visualType.getConstructor().newInstance();
            for (String option : List.of("ShowSide", "ShowOutline", "ShowEyeHeight", "ShowViewRay", "HoverColor", "IframeColor"))
                visualType.getMethod("set" + option, boolean.class).invoke(visual, false);
            visualType.getMethod("setDrawOverEntity", boolean.class).invoke(visual, true);
            Method draw = null;
            for (Method method : rendererType.getDeclaredMethods())
                if (method.getName().equals("drawEntity") && method.getParameterCount() == 3) draw = method;
            require(draw != null, "The bundled hitbox renderer no longer exposes its entity draw path");
            draw.setAccessible(true);

            QaPlayer target = new QaPlayer(mc.world, new GameProfile(UUID.fromString("c7bf3ba6-56f3-4df8-a767-365bf9763562"), "BoundsTest"));
            target.setPosition(12.25, 64.5, -7.75);
            target.prevX = target.x - 0.7; target.prevY = target.y + 0.2; target.prevZ = target.z - 1.3;
            Box player = target.getShape();
            Box[] boxes = {
                player,
                new Box(target.x - 0.125, target.y + 0.03, target.z - 0.2, target.x + 0.625, target.y + 1.25, target.z + 0.4),
                new Box(target.x - 0.125, target.y, target.z - 0.125, target.x + 0.125, target.y + 0.25, target.z + 0.125)
            };
            float pickRadius = target.getPickRadius();
            require(pickRadius > 0.0F, "The hitbox regression fixture needs a nonzero vanilla picking margin");
            field(rendererType, "camX").setDouble(null, 10.0);
            field(rendererType, "camY").setDouble(null, 63.0);
            field(rendererType, "camZ").setDouble(null, -4.0);
            field(rendererType, "fwdX").setDouble(null, 0.0);
            field(rendererType, "fwdY").setDouble(null, 0.0);
            field(rendererType, "fwdZ").setDouble(null, 1.0);
            field(rendererType, "ribbonScale").setDouble(null, 0.0);

            for (boolean savedExpansion : new boolean[]{false, true}) {
                margin.setBoolean(null, savedExpansion);
                require(!(Boolean) configType.getMethod("getAccurateHitboxes").invoke(config),
                        "A saved expanded-bounds setting still enlarges rendered boxes");
                for (int shape = 0; shape < boxes.length; shape++) {
                    Box box = boxes[shape]; target.setShape(box);
                    for (float tick : new float[]{0.0F, 0.375F, 1.0F}) {
                        field(rendererType, "partialTicks").setFloat(null, tick);
                        draw.invoke(renderer, null, target, visual);
                        double dx = target.prevX + (target.x - target.prevX) * tick - target.x - 10.0;
                        double dy = target.prevY + (target.y - target.prevY) * tick - target.y - 63.0;
                        double dz = target.prevZ + (target.z - target.prevZ) * tick - target.z + 4.0;
                        double[] expected = {box.minX + dx, box.minY + dy, box.minZ + dz, box.maxX + dx, box.maxY + dy, box.maxZ + dz};
                        double[] actual = bounds(rendererType);
                        for (int axis = 0; axis < expected.length; axis++)
                            require(Math.abs(expected[axis] - actual[axis]) < 1.0E-10,
                                    "Hitbox differs from vanilla at shape " + shape + ", partial tick " + tick + ", bound " + axis);
                        require(target.getShape() == box && target.getPickRadius() == pickRadius,
                                "Rendering altered the entity collision shape or picking margin");
                        samples.add(Map.of("savedExpansion", savedExpansion, "shape", shape, "partialTick", tick,
                                "expected", expected, "rendered", actual));
                    }
                }
            }
            checks.put("hitboxBounds038", Map.of("passed", true, "samples", samples, "vanillaPickingMargin", pickRadius,
                    "playerDimensions", List.of(player.maxX - player.minX, player.maxY - player.minY, player.maxZ - player.minZ),
                    "hiddenExpandedBoundsOption", true, "entityGeometryPreserved", true));
        } finally {
            margin.set(null, previousMargin);
            for (Map.Entry<Field, Object> saved : previousState.entrySet()) saved.getKey().set(null, saved.getValue());
        }
    }

    private static double[] bounds(Class<?> renderer) throws Exception {
        double[] bounds = new double[6];
        String[] names = {"gateMinX", "gateMinY", "gateMinZ", "gateMaxX", "gateMaxY", "gateMaxZ"};
        for (int i = 0; i < names.length; i++) bounds[i] = field(renderer, names[i]).getDouble(null);
        return bounds;
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
