// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features;

import java.util.ArrayList;
import java.util.Random;
import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.BufferUtils;

public final class KillEffects {
    private record Effect(double x, double y, double z, long born, int type, float life, int color, float[] samples) {}
    private static final KillEffectTracker TRACKER = new KillEffectTracker();
    private static final ArrayList<Effect> ACTIVE = new ArrayList<>(8);
    private static final int[] COLORS = {0xF2F5FF, 0xF05A5A, 0xFFA34D, 0x75E69A, 0x6BD9EA, 0xE88DBA};
    private static World world;
    private static long ticks;
    private static int confirmed;
    private static int rendered;
    private static final FloatBuffer COLOR = BufferUtils.createFloatBuffer(4);

    private KillEffects() {}

    public static void attack(PlayerEntity player, Entity target) {
        Minecraft mc = Minecraft.getInstance();
        if (!KillEffectsConfig.getEnabled() || player != mc.player || mc.world == null ||
                !(target instanceof LivingEntity living) || !living.isAlive() ||
                (KillEffectsConfig.getPlayersOnly() && !(target instanceof PlayerEntity))) return;
        synchronizeWorld(mc.world);
        TRACKER.hit(target.getNetworkId(), target.getUuid(), ticks);
    }

    public static void death(Entity target) {
        Minecraft mc = Minecraft.getInstance();
        if (!KillEffectsConfig.getEnabled() || target == null || target.world != mc.world || target == mc.player) return;
        synchronizeWorld(mc.world);
        if (!TRACKER.confirm(target.getNetworkId(), target.getUuid(), ticks, window())) return;
        if (ACTIVE.size() >= 8) ACTIVE.remove(0);
        int type = Math.max(0, Math.min(4, KillEffectsConfig.getEffect()));
        int count = Math.max(8, Math.min(64, KillEffectsConfig.getCount()));
        Random random = new Random(target.getUuid().getLeastSignificantBits() ^ ticks);
        float[] samples = new float[count * 3];
        for (int i = 0; i < samples.length; i++) samples[i] = random.nextFloat() * 2 - 1;
        ACTIVE.add(new Effect(target.x, target.y, target.z, System.nanoTime(), type,
                Math.max(0.3f, Math.min(2f, KillEffectsConfig.getLifetime())),
                COLORS[Math.max(0, Math.min(COLORS.length - 1, KillEffectsConfig.getColor()))], samples));
        confirmed++;
        String sound = switch (KillEffectsConfig.getSound()) {
            case 1 -> "ambient.weather.thunder";
            case 2 -> "random.orb";
            case 3 -> "note.pling";
            default -> null;
        };
        if (sound != null) mc.world.playSound(target.x, target.y, target.z, sound,
                Math.max(0, Math.min(1, KillEffectsConfig.getVolume())), 1f, false);
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        synchronizeWorld(mc.world);
        ticks++;
        if (!KillEffectsConfig.getEnabled() || mc.world == null || mc.player == null) {
            TRACKER.clear();
            ACTIVE.clear();
            return;
        }
        TRACKER.expire(ticks, window());
        for (KillEffectTracker.Hit hit : TRACKER.pending()) {
            Entity target = mc.world.getEntity(hit.entityId());
            if (target instanceof LivingEntity living && living.getUuid().equals(hit.uuid()) && living.getHealth() <= 0)
                death(target);
        }
        long now = System.nanoTime();
        ACTIVE.removeIf(effect -> now - effect.born > effect.life * 1_000_000_000L);
    }

    private static int window() { return Math.max(1, Math.min(10, KillEffectsConfig.getAttackWindow())) * 20; }

    private static void synchronizeWorld(World next) {
        if (world == next) return;
        world = next;
        ticks = 0;
        TRACKER.clear();
        ACTIVE.clear();
    }

    public static void render(float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (!KillEffectsConfig.getEnabled() || ACTIVE.isEmpty() || mc.world == null || mc.options.hideGui) return;
        Entity camera = mc.getCamera();
        if (camera == null) return;
        double cameraX = camera.prevX + (camera.x - camera.prevX) * tickDelta;
        double cameraY = camera.prevY + (camera.y - camera.prevY) * tickDelta;
        double cameraZ = camera.prevZ + (camera.z - camera.prevZ) * tickDelta;
        float oldWidth = GL11.glGetFloat(GL11.GL_LINE_WIDTH);
        boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int src = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        COLOR.clear();
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, COLOR);
        GlStateManager.pushMatrix();
        GlStateManager.translated(-cameraX, -cameraY, -cameraZ);
        GlStateManager.disableTexture();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);
        GlStateManager.depthMask(false);
        try {
            long now = System.nanoTime();
            for (Effect effect : ACTIVE) {
                float age = Math.max(0, (now - effect.born) / 1_000_000_000f);
                float progress = age / effect.life;
                if (progress >= 1) continue;
                if (effect.type == 0) lightning(effect, progress);
                else if (effect.type == 4) ring(effect, progress);
                else particles(effect, age, progress, camera.yaw);
            }
            rendered++;
        } finally {
            GL11.glLineWidth(oldWidth);
            GlStateManager.depthMask(depthMask);
            if (texture) GlStateManager.enableTexture();
            if (lighting) GlStateManager.enableLighting();
            if (cull) GlStateManager.enableCull();
            GlStateManager.blendFuncSeparate(src, dst, srcAlpha, dstAlpha);
            if (!blend) GlStateManager.disableBlend();
            GlStateManager.clearColor();
            GlStateManager.color4f(COLOR.get(0), COLOR.get(1), COLOR.get(2), COLOR.get(3));
            GlStateManager.popMatrix();
        }
    }

    private static void lightning(Effect e, float progress) {
        GL11.glLineWidth(2.5f);
        BufferBuilder b = Tesselator.getInstance().getBuffer();
        b.begin(GL11.GL_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= 12; i++) {
            double wobble = (i == 0 || i == 12) ? 0 : 0.55;
            vertex(b, e.x + e.samples[i * 3 % e.samples.length] * wobble, e.y + i * 0.7,
                    e.z + e.samples[(i * 3 + 1) % e.samples.length] * wobble, e.color, 1 - progress);
        }
        Tesselator.getInstance().end();
        ring(e, progress);
    }

    private static void ring(Effect e, float progress) {
        BufferBuilder b = Tesselator.getInstance().getBuffer();
        b.begin(GL11.GL_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        double radius = 0.25 + progress * 1.8;
        for (int i = 0; i <= 48; i++) {
            double a = i * Math.PI / 24;
            vertex(b, e.x + Math.cos(a) * radius, e.y + 0.05 + progress * 0.1,
                    e.z + Math.sin(a) * radius, e.color, 1 - progress);
        }
        Tesselator.getInstance().end();
    }

    private static void particles(Effect e, float age, float progress, float yaw) {
        BufferBuilder b = Tesselator.getInstance().getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_COLOR);
        double rightX = Math.cos(Math.toRadians(yaw)), rightZ = Math.sin(Math.toRadians(yaw));
        for (int i = 0; i < e.samples.length; i += 3) {
            double x = e.x + e.samples[i] * age * 2;
            double y = e.y + 0.9 + (e.samples[i + 1] + 1.2) * age - age * age * (e.type == 2 ? 0.3 : 1.8);
            double z = e.z + e.samples[i + 2] * age * 2;
            double size = (e.type == 3 ? 0.07 : 0.045) * (1 - progress * 0.6);
            int color = e.type == 2 ? 0xFFA34D : e.type == 3 ? 0xF05A76 : e.color;
            if (e.type == 3) {
                for (int py = 0; py < 5; py++) for (int px = 0; px < 5; px++) {
                    if ((py == 0 && px % 2 == 0) || (py == 3 && (px == 0 || px == 4)) || (py == 4 && px != 2)) continue;
                    quad(b, x + rightX * (px - 2) * size, y + (2 - py) * size,
                            z + rightZ * (px - 2) * size, rightX * size, rightZ * size, size, color, 1 - progress);
                }
            } else quad(b, x, y, z, rightX * size, rightZ * size, size, color, 1 - progress);
        }
        Tesselator.getInstance().end();
    }

    private static void quad(BufferBuilder b, double x, double y, double z, double rx, double rz, double sy, int color, float alpha) {
        vertex(b, x - rx / 2, y - sy / 2, z - rz / 2, color, alpha);
        vertex(b, x + rx / 2, y - sy / 2, z + rz / 2, color, alpha);
        vertex(b, x + rx / 2, y + sy / 2, z + rz / 2, color, alpha);
        vertex(b, x - rx / 2, y + sy / 2, z - rz / 2, color, alpha);
    }

    private static void vertex(BufferBuilder b, double x, double y, double z, int color, float alpha) {
        b.vertex(x, y, z).color(color >> 16 & 255, color >> 8 & 255, color & 255, (int) (Math.max(0, alpha) * 255)).nextVertex();
    }

    public static int confirmedCount() { return confirmed; }
    public static int renderedCount() { return rendered; }
    public static int activeCount() { return ACTIVE.size(); }
    public static int trackedCount() { return TRACKER.size(); }
}
