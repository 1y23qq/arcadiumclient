// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.LegacyComposeQa.*;

import java.lang.reflect.Field;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.particle.Particle;
import net.minecraft.client.entity.particle.RainSplashParticle;
import net.minecraft.client.render.vertex.Tesselator;
import org.lwjgl.opengl.GL11;

final class LegacyParticles040Qa {
    private static final String PROVIDER = "org.polyfrost.overflowparticles.";
    private static boolean complete;

    private LegacyParticles040Qa() {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    static boolean tick(Minecraft client, Map<String, Object> result) throws Exception {
        if (complete) return true;
        require(client.world != null && client.player != null, "Particle QA requires the native test world");
        Object critical = config("crit");
        Object rain = config("rain");
        Object main = instance(PROVIDER + "client.config.OverflowParticlesConfig");
        Map<String, Object> saved = new LinkedHashMap<>();
        for (String name : List.of("Enabled", "Multiplier", "Size", "CustomColor", "ColorMode", "Color", "Fade", "FadeStart"))
            saved.put(name, call(critical, "get" + name));
        boolean rainEnabled = (boolean) call(rain, "getEnabled");
        boolean noClip = (boolean) call(main, "getParticleNoClip");
        int particlesSetting = client.options.particles;
        boolean multiplied = (boolean) stat(PROVIDER + "client.utils.ParticleSpawner", "isMultiplied");
        List[] layers = layers(client);
        List<List> backup = new ArrayList<>();
        for (List layer : layers) backup.add(new ArrayList(layer));
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        try {
            client.options.particles = 0;
            stat(PROVIDER + "client.utils.ParticleSpawner", "setMultiplied", false);
            call(critical, "setEnabled", true); call(critical, "setMultiplier", 1f);
            call(critical, "setCustomColor", false); call(critical, "setFade", false); call(critical, "setSize", 1f);
            double x = client.player.x, y = client.player.shape.minY + 0.5, z = client.player.z;
            Map<String, Object> evidence = new LinkedHashMap<>();
            List<Map<String, Object>> names = new ArrayList<>();
            Particle crit = null;
            for (String name : List.of("crit", "magicCrit", "blockcrack_1_0", "iconcrack_1_0")) {
                Particle particle = client.worldRenderer.tryAddParticle(name, x, y, z, 0, 0, 0);
                require(particle != null, "Native particle factory rejected " + name);
                Object expected = stat(PROVIDER + "utils.NativeParticleType", "fromName", name);
                Object actual = stat(PROVIDER + "utils.ParticleIdentifier", "get", particle);
                require(expected == actual, "The particle type lost its native identity: " + name);
                names.add(Map.of("nativeName", name, "type", ((Enum<?>) actual).name(), "created", true));
                if (name.equals("crit")) crit = particle;
            }
            evidence.put("nativeFactoryTypes", names);
            call(critical, "setEnabled", false);
            int before = count(layers);
            require(client.worldRenderer.tryAddParticle("crit", x, y, z, 0, 0, 0) == null && count(layers) == before,
                "The per-type enable control did not suppress native creation");
            evidence.put("disabledTypeSuppressed", true);
            call(critical, "setEnabled", true); call(critical, "setMultiplier", 2f);
            before = count(layers);
            client.world.addParticle("crit", x, y, z, 0, 0, 0);
            require(count(layers) - before == 2, "The world string-spawn multiplier did not create exactly two effects");
            evidence.put("worldMultiplierTwo", true);
            call(critical, "setMultiplier", 1f);
            call(rain, "setEnabled", true);
            Particle rawRain = new RainSplashParticle(client.world, x, y, z);
            client.particleManager.add(rawRain);
            require(stat(PROVIDER + "utils.ParticleIdentifier", "get", rawRain)
                == stat(PROVIDER + "utils.NativeParticleType", "fromName", "rain"), "Raw weather particles were not classified");
            call(rain, "setEnabled", false); before = count(layers);
            client.particleManager.add(new RainSplashParticle(client.world, x, y, z));
            require(count(layers) == before, "The rain control did not suppress raw weather effects");
            evidence.put("rawRainIdentificationAndSuppression", true);

            int[] first = quad(crit);
            call(critical, "setSize", 2f);
            int[] doubled = quad(crit);
            double width = span(first, 0), height = span(first, 1);
            require(width > 0 && height > 0, "The native particle produced an empty quad");
            require(Math.abs(span(doubled, 0) / width - 2) < 0.002 && Math.abs(span(doubled, 1) / height - 2) < 0.002,
                "The particle size control did not scale actual native vertices");
            evidence.put("quadSizeRatio", span(doubled, 0) / width);
            call(critical, "setSize", 1f); call(critical, "setCustomColor", true); call(critical, "setColorMode", 1);
            Object color = Class.forName("org.polyfrost.compose.render.PolyColor").getConstructor(int.class).newInstance(0xFF3366CC);
            call(critical, "setColor", color);
            int[] colored = quad(crit);
            int expectedColor = packed(51, 102, 204, 255);
            for (int i = 0; i < 4; i++) require(colored[i * 8 + 5] == expectedColor,
                "The color override was not applied to actual Tesselator vertices");
            evidence.put("nativeVertexOverrideArgb", "FF3366CC");
            call(critical, "setCustomColor", false); call(critical, "setFade", true); call(critical, "setFadeStart", 0f);
            int lifetime = ((Number) call(crit, "getLifetime")).intValue();
            require(lifetime > 1, "The native critical particle has no observable lifespan");
            for (int i = 0; i < lifetime / 2; i++) crit.tick();
            crit.setColor(1f, 1f, 1f); crit.setAlpha(0.8f);
            for (List layer : layers) layer.clear();
            layers[crit.getAtlasType()].add(crit);
            Tesselator.INSTANCE.color(17, 43, 97, 161);
            Field colorField = integerField(Tesselator.INSTANCE, packed(17, 43, 97, 161));
            float initialAlpha = ((Number) call(crit, "getAlpha")).floatValue();
            int age = ((Number) call(crit, "getAge")).intValue();
            client.particleManager.render(client.player, 0f);
            int expectedAlpha = (int) (initialAlpha * (1f - (float) age / lifetime) * 255f);
            int actualColor = colorField.getInt(Tesselator.INSTANCE);
            int actualAlpha = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? actualColor >>> 24 : actualColor & 255;
            require(Math.abs(actualAlpha - expectedAlpha) <= 1 && actualAlpha < 204,
                "The native fade did not affect the rendered alpha: " + actualAlpha + " expected " + expectedAlpha);
            require(((Number) call(crit, "getAlpha")).floatValue() == initialAlpha,
                "Rendering permanently multiplied the particle alpha");
            client.particleManager.render(client.player, 0f);
            require(((Number) call(crit, "getAlpha")).floatValue() == initialAlpha,
                "Repeated rendering made particle fading depend on the frame count");
            require(!(boolean) stat(PROVIDER + "client.OverflowParticlesClient", "isRendering")
                && stat(PROVIDER + "client.OverflowParticlesClient", "getRenderingEntity") == null,
                "The particle color/render context leaked after drawing");
            evidence.put("renderedFadeAlpha", actualAlpha);
            evidence.put("fadeAndRenderStateRestored", true);

            crit.setPosition(x, client.player.shape.minY + 0.3, z); crit.noClip = false;
            call(main, "setParticleNoClip", false);
            double initialY = crit.y; crit.move(0, -2, 0);
            require(crit.y > initialY - 1.5, "The no-clip comparison has no ground collision");
            crit.setPosition(x, initialY, z); call(main, "setParticleNoClip", true);
            crit.move(0, -2, 0);
            require(Math.abs(crit.y - (initialY - 2)) < 0.00001 && !crit.noClip,
                "The native no-clip movement failed or left the entity flag changed");
            evidence.put("nativeNoClipAndFlagRestoration", true);
            result.put("nativeParticles", evidence);
            complete = true;
            return true;
        } finally {
            for (Map.Entry<String, Object> entry : saved.entrySet()) call(critical, "set" + entry.getKey(), entry.getValue());
            call(rain, "setEnabled", rainEnabled); call(main, "setParticleNoClip", noClip);
            client.options.particles = particlesSetting;
            stat(PROVIDER + "client.utils.ParticleSpawner", "setMultiplied", multiplied);
            stat(PROVIDER + "client.OverflowParticlesClient", "setRendering", false);
            stat(PROVIDER + "client.OverflowParticlesClient", "setRenderingEntity", (Object) null);
            for (int i = 0; i < layers.length; i++) { layers[i].clear(); layers[i].addAll(backup.get(i)); }
            GL11.glPopClientAttrib(); GL11.glPopAttrib();
        }
    }

    private static Object config(String name) throws Exception {
        Object type = stat(PROVIDER + "utils.NativeParticleType", "fromName", name);
        Object config = stat(PROVIDER + "client.config.PerParticleConfigManager", "getConfigByType", type);
        require(config != null, "The native particle config is missing: " + name);
        return config;
    }

    private static List[] layers(Minecraft client) throws Exception {
        for (Field field : client.particleManager.getClass().getDeclaredFields()) {
            Class<?> type = field.getType();
            if (type.isArray() && type.componentType() == List.class) {
                field.setAccessible(true); return (List[]) field.get(client.particleManager);
            }
        }
        throw new IllegalStateException("The native particle layer lists were not found");
    }

    private static int count(List[] layers) { int total = 0; for (List layer : layers) total += layer.size(); return total; }

    private static int[] quad(Particle particle) throws Exception {
        Tesselator tess = Tesselator.INSTANCE;
        Object previous = stat(PROVIDER + "client.OverflowParticlesClient", "getRenderingEntity");
        boolean rendering = (boolean) stat(PROVIDER + "client.OverflowParticlesClient", "isRendering");
        tess.begin(GL11.GL_QUADS);
        try {
            stat(PROVIDER + "client.OverflowParticlesClient", "setRenderingEntity", particle);
            stat(PROVIDER + "client.OverflowParticlesClient", "setRendering", true);
            particle.render(tess, 0.5f, 1f, 1f, 0f, 0f, 0f);
            Object state = tess.buildState(0f, 0f, 0f);
            for (Field field : state.getClass().getDeclaredFields()) if (field.getType() == int[].class) {
                field.setAccessible(true); int[] vertices = ((int[]) field.get(state)).clone();
                require(vertices.length == 32, "The native particle did not produce four complete vertices");
                return vertices;
            }
            throw new IllegalStateException("The native quad vertex data was not found");
        } finally {
            stat(PROVIDER + "client.OverflowParticlesClient", "setRendering", rendering);
            stat(PROVIDER + "client.OverflowParticlesClient", "setRenderingEntity", previous);
            tess.end();
        }
    }

    private static double span(int[] vertices, int component) {
        double minimum = Double.POSITIVE_INFINITY, maximum = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            double value = Float.intBitsToFloat(vertices[i * 8 + component]);
            minimum = Math.min(minimum, value); maximum = Math.max(maximum, value);
        }
        return maximum - minimum;
    }

    private static int packed(int r, int g, int b, int a) {
        return ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? a << 24 | b << 16 | g << 8 | r : r << 24 | g << 16 | b << 8 | a;
    }

    private static Field integerField(Object target, int value) throws Exception {
        Field found = null;
        for (Field field : target.getClass().getDeclaredFields()) if (field.getType() == int.class) {
            field.setAccessible(true);
            if (field.getInt(target) == value) { require(found == null, "The native color field is ambiguous"); found = field; }
        }
        require(found != null, "The native color field was not found");
        return found;
    }
}
