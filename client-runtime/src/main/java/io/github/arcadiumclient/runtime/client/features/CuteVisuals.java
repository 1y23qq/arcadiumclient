// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features;

import java.awt.Color;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerHandActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

public final class CuteVisuals {
    private static final CuteVisualsState STATE = new CuteVisualsState();
    private static final String[] HEART_ROWS = {"01100110", "11111111", "11111111", "01111110", "00111100", "00011000"};
    private static CuteVisualsState.Options options = configuration();
    private static World world;
    private static Entity player;
    private static boolean active;
    private static int renders, breaks, batches;
    private static BufferBuilder vertices;
    private static double cameraX, cameraY, cameraZ, rightX, rightZ, upX, upY, upZ;

    private CuteVisuals() { }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ready(minecraft)) return;
        options = configuration();
        STATE.tick(now(), minecraft.player.x, minecraft.player.y, minecraft.player.z, options);
    }

    public static void packetSent(Packet packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(packet instanceof PlayerHandActionC2SPacket action) || !minecraft.isOnSameThread() || !ready(minecraft)) return;
        CuteVisualsState.Dig event = switch (action.getAction()) {
            case START_DESTROY_BLOCK -> CuteVisualsState.Dig.BEGIN;
            case STOP_DESTROY_BLOCK -> CuteVisualsState.Dig.FINISH;
            case ABORT_DESTROY_BLOCK -> CuteVisualsState.Dig.CANCEL;
            default -> null;
        };
        BlockPos position = action.getPos();
        if (event == null || position == null) return;
        boolean bed = minecraft.world.getBlockState(position).getBlock() == Blocks.BED;
        if (STATE.dig(event, position.getX(), position.getY(), position.getZ(), bed, minecraft.player.abilities.creativeMode)) {
            options = configuration();
            STATE.burst(now(), position.getX() + .5, position.getY() + .5, position.getZ() + .5,
                    minecraft.player.x, minecraft.player.z, options);
            breaks++;
            if (CuteVisualsConfig.getBedSound()) minecraft.player.playSound("random.orb", .7f, 1.8f);
        }
    }

    private static boolean ready(Minecraft minecraft) {
        boolean enabled = CuteVisualsConfig.getEnabled() && minecraft.world != null && minecraft.player != null;
        if (world != minecraft.world || player != minecraft.player || active && !enabled) STATE.clear();
        world = minecraft.world; player = minecraft.player; active = enabled;
        return enabled;
    }

    private static CuteVisualsState.Options configuration() {
        return new CuteVisualsState.Options(CuteVisualsConfig.getHearts(), CuteVisualsConfig.getDots(),
                CuteVisualsConfig.getOnlyWhileMoving(), CuteVisualsConfig.getPulse(), CuteVisualsConfig.getBedBurst(),
                CuteVisualsConfig.getRainbow(), CuteVisualsConfig.getHeartsSpawnRate(), CuteVisualsConfig.getHeartsLifetime(),
                CuteVisualsConfig.getDotsSpawnRate(), CuteVisualsConfig.getDotsLifetime(), CuteVisualsConfig.getBedBurstCount(),
                CuteVisualsConfig.getBedBurstLifetime(), CuteVisualsConfig.getBedBurstSize(), CuteVisualsConfig.getBedBurstSpeed(),
                CuteVisualsConfig.getRainbowDuration(), CuteVisualsConfig.getRainbowLineWidth(), CuteVisualsConfig.getOpacity() / 100f);
    }

    public static void render(float delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ready(minecraft) || STATE.count() == 0 || minecraft.options.hideGui) return;
        Entity camera = minecraft.getCamera();
        if (camera == null) return;
        long time = now();
        STATE.expire(time, options);
        cameraX = camera.prevX + (camera.x - camera.prevX) * delta;
        cameraY = camera.prevY + (camera.y - camera.prevY) * delta;
        cameraZ = camera.prevZ + (camera.z - camera.prevZ) * delta;
        double yaw = Math.toRadians(minecraft.getEntityRenderDispatcher().cameraYaw);
        double pitch = Math.toRadians(minecraft.getEntityRenderDispatcher().cameraPitch);
        rightX = Math.cos(yaw); rightZ = Math.sin(yaw);
        upX = -rightZ * Math.sin(pitch); upY = Math.cos(pitch); upZ = rightX * Math.sin(pitch);
        Tesselator tesselator = Tesselator.getInstance();
        vertices = tesselator.getBuffer();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GL11.glDepthMask(false);
            vertices.begin(GL11.GL_TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            for (CuteVisualsState.Sprite sprite : STATE.sprites()) {
                if (!sprite.alive(time)) continue;
                float progress = sprite.progress(time);
                float opacity = CuteVisualsState.opacity(progress) * options.opacity();
                if (sprite.kind == CuteVisualsState.Kind.RAINBOW) rainbow(sprite, progress, opacity);
                else billboard(sprite, time, opacity);
            }
            tesselator.end();
            batches++; renders++;
        } finally {
            GL11.glPopAttrib();
            GlStateManager.clearColor();
            vertices = null;
        }
    }

    private static void billboard(CuteVisualsState.Sprite sprite, long time, float opacity) {
        double seconds = (time - sprite.born) / 1000.0;
        double x = sprite.x + sprite.vx * seconds, y = sprite.y + sprite.vy * seconds, z = sprite.z + sprite.vz * seconds;
        if (sprite.kind == CuteVisualsState.Kind.CONFETTI) y -= .65 * seconds * seconds;
        double size = sprite.size * (options.pulse() ? 1 + Math.sin(seconds * 7) * .12 : 1);
        int color = Color.HSBtoRGB(sprite.hue, .48f, 1f);
        if (sprite.kind == CuteVisualsState.Kind.HEART || sprite.kind == CuteVisualsState.Kind.CONFETTI && sprite.variant == 0) {
            for (int row = 0; row < HEART_ROWS.length; row++) {
                String pixels = HEART_ROWS[row];
                for (int column = 0; column < pixels.length();) {
                    if (pixels.charAt(column) == '0') { column++; continue; }
                    int begin = column;
                    while (column < pixels.length() && pixels.charAt(column) == '1') column++;
                    double left = (begin / 8.0 - .5) * size, right = (column / 8.0 - .5) * size;
                    double top = (.5 - row / 6.0) * size, bottom = (.5 - (row + 1) / 6.0) * size;
                    flatQuad(x, y, z, left, bottom, right, top, color, opacity);
                }
            }
        } else {
            int sides = sprite.kind == CuteVisualsState.Kind.SPARK ? 12 : 4;
            for (int i = 0; i < sides; i++) {
                double a = Math.TAU * i / sides + sprite.angle, b = Math.TAU * (i + 1) / sides + sprite.angle;
                flat(x, y, z, 0, 0, color, opacity);
                flat(x, y, z, Math.cos(a) * size, Math.sin(a) * size, color, sprite.kind == CuteVisualsState.Kind.SPARK ? 0 : opacity);
                flat(x, y, z, Math.cos(b) * size, Math.sin(b) * size, color, sprite.kind == CuteVisualsState.Kind.SPARK ? 0 : opacity);
            }
        }
    }

    private static void rainbow(CuteVisualsState.Sprite sprite, float progress, float opacity) {
        double radius = sprite.size * Math.min(1, Math.sqrt(progress * 5));
        double sideX = Math.cos(sprite.angle), sideZ = -Math.sin(sprite.angle);
        double band = options.rainbowWidth() / 180.0;
        for (int stripe = 0; stripe < 7; stripe++) {
            int color = Color.HSBtoRGB(stripe / 8f, .62f, 1f);
            double outer = radius + stripe * band, inner = outer + band;
            for (int segment = 0; segment < 24; segment++) {
                double a = Math.PI * segment / 24, b = Math.PI * (segment + 1) / 24;
                arcVertex(sprite, sideX, sideZ, a, outer, color, opacity);
                arcVertex(sprite, sideX, sideZ, a, inner, color, opacity);
                arcVertex(sprite, sideX, sideZ, b, inner, color, opacity);
                arcVertex(sprite, sideX, sideZ, a, outer, color, opacity);
                arcVertex(sprite, sideX, sideZ, b, inner, color, opacity);
                arcVertex(sprite, sideX, sideZ, b, outer, color, opacity);
            }
        }
    }

    private static void arcVertex(CuteVisualsState.Sprite sprite, double sideX, double sideZ,
                                  double angle, double radius, int color, float opacity) {
        double horizontal = Math.cos(angle) * radius;
        vertex(sprite.x + horizontal * sideX, sprite.y + Math.sin(angle) * radius,
                sprite.z + horizontal * sideZ, color, opacity);
    }

    private static void flatQuad(double x, double y, double z, double left, double bottom,
                                  double right, double top, int color, float opacity) {
        flat(x, y, z, left, bottom, color, opacity); flat(x, y, z, right, bottom, color, opacity);
        flat(x, y, z, right, top, color, opacity); flat(x, y, z, left, bottom, color, opacity);
        flat(x, y, z, right, top, color, opacity); flat(x, y, z, left, top, color, opacity);
    }

    private static void flat(double x, double y, double z, double horizontal, double vertical, int color, float opacity) {
        vertex(x + rightX * horizontal + upX * vertical, y + upY * vertical,
                z + rightZ * horizontal + upZ * vertical, color, opacity);
    }

    private static void vertex(double x, double y, double z, int color, float opacity) {
        vertices.vertex(x - cameraX, y - cameraY, z - cameraZ)
                .color((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, opacity).nextVertex();
    }

    private static long now() { return System.nanoTime() / 1_000_000; }
    public static int activeCount() { return STATE.count(); }
    public static int heartCount() { return STATE.count(CuteVisualsState.Kind.HEART); }
    public static int dotCount() { return STATE.count(CuteVisualsState.Kind.SPARK); }
    public static int bedParticleCount() { return STATE.count(CuteVisualsState.Kind.CONFETTI); }
    public static int rainbowCount() { return STATE.count(CuteVisualsState.Kind.RAINBOW); }
    public static int renderedCount() { return renders; }
    public static int breakCount() { return breaks; }
    public static int batchCount() { return batches; }
}
