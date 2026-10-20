// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.skins;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import io.github.arcadiumclient.runtime.client.features.AnimationsConfig;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.texture.HttpTexture;
import net.minecraft.resource.Identifier;
import io.github.arcadiumclient.runtime.mixin.client.access.HttpSkinImageAccessor;
import org.lwjgl.opengl.GL11;

public final class LegacyPlayerSkins {
    private static final Map<Identifier, Boolean> SLIM = java.util.Collections.synchronizedMap(new LinkedHashMap<>(32, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Identifier, Boolean> entry) { return size() > 256; }
    });
    private static final Map<UUID, Boolean> PROFILES = java.util.Collections.synchronizedMap(new LinkedHashMap<>(32, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> entry) { return size() > 256; }
    });
    private static final LinkedHashMap<Identifier, Skin> SKINS = new LinkedHashMap<>(32, .75f, true);

    private LegacyPlayerSkins() {}

    public static void model(Identifier texture, boolean slim) { if (texture != null) SLIM.put(texture, slim); }

    public static void profile(GameProfile profile) {
        if (profile == null || profile.getId() == null) return;
        PROFILES.remove(profile.getId());
        for (var property : profile.getProperties().get("textures")) {
            String value = property.getValue();
            if (value == null || value.length() > 65536) continue;
            try {
                var textures = new JsonParser().parse(new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("textures");
                var skin = textures == null ? null : textures.getAsJsonObject("SKIN");
                if (skin == null) continue;
                var metadata = skin.getAsJsonObject("metadata");
                boolean slim = metadata != null && metadata.has("model") && "slim".equals(metadata.get("model").getAsString());
                PROFILES.put(profile.getId(), slim);
                return;
            } catch (RuntimeException ignored) { }
        }
    }

    public static boolean slim(ClientPlayerEntity player) {
        var animations = AnimationsConfig.badlion();
        if (animations != null && animations.noSmallArms()) return false;
        String override = SkinChanger.modelFor(player.getUuid());
        if (override != null) return override.equals("slim");
        Boolean profile = PROFILES.get(player.getUuid());
        return profile != null ? profile : Boolean.TRUE.equals(SLIM.get(player.getSkinTextureLocation()));
    }

    public static Skin skin(ClientPlayerEntity player) {
        Identifier id = player.getSkinTextureLocation();
        Object texture = Minecraft.getInstance().getTextureManager().get(id);
        if (!(texture instanceof HttpTexture) || !(texture instanceof HttpSkinImageAccessor access)) return null;
        BufferedImage image = access.arcadium$skinImage();
        if (image == null || image.getWidth() != 64 || image.getHeight() != 64) return null;
        Skin skin = SKINS.get(id);
        if (skin != null && skin.image == image) return skin;
        if (skin != null) skin.close();
        skin = new Skin(image);
        SKINS.put(id, skin);
        while (SKINS.size() > 128) {
            var first = SKINS.entrySet().iterator();
            first.next().getValue().close();
            first.remove();
        }
        return skin;
    }

    public static void discard(Object texture) {
        Skin previous = SKINS.remove(texture);
        if (previous != null) previous.close();
        SLIM.remove(texture);
    }

    public static void clear() {
        for (Skin skin : SKINS.values()) skin.close();
        SKINS.clear();
    }

    public static final class Skin {
        private final BufferedImage image;
        private final int[] lists = new int[12];

        private Skin(BufferedImage image) { this.image = image; }

        public void draw(int bodyPart, boolean slim, LegacySkinMesh.Box box) {
            int index = bodyPart + (slim ? 6 : 0);
            int list = lists[index];
            if (list == 0) {
                list = GL11.glGenLists(1);
                if (list == 0) return;
                float[] mesh = LegacySkinMesh.build(image, box);
                GL11.glNewList(list, GL11.GL_COMPILE);
                GL11.glBegin(GL11.GL_QUADS);
                for (int vertex = 0; vertex < mesh.length; vertex += 8) {
                    GL11.glNormal3f(mesh[vertex], mesh[vertex + 1], mesh[vertex + 2]);
                    GL11.glTexCoord2f(mesh[vertex + 6], mesh[vertex + 7]);
                    GL11.glVertex3f(mesh[vertex + 3], mesh[vertex + 4], mesh[vertex + 5]);
                }
                GL11.glEnd();
                GL11.glEndList();
                lists[index] = list;
            }
            GL11.glCallList(list);
        }

        private void close() {
            for (int list : lists) if (list != 0) GL11.glDeleteLists(list, 1);
        }
    }
}
