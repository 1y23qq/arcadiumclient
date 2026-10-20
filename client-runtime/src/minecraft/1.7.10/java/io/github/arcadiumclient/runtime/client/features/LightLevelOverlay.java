// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import io.github.arcadiumclient.runtime.legacy.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.TextRenderer;
import io.github.arcadiumclient.runtime.legacy.GlStateManager;
import io.github.arcadiumclient.runtime.legacy.BufferBuilder;
import io.github.arcadiumclient.runtime.legacy.DefaultVertexFormat;
import io.github.arcadiumclient.runtime.legacy.Tesselator;
import net.minecraft.entity.Entity;
import io.github.arcadiumclient.runtime.legacy.BlockPos;
import io.github.arcadiumclient.runtime.legacy.LegacyWorld;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSource;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.WorldChunkSection;
import org.polyfrost.oneconfig.internal.FontPreferences;

public final class LightLevelOverlay {
    public record Cell(int x, int z, double top, int light) {}

    private static final long SCAN_INTERVAL_NS = 250_000_000L;
    private static final String[] LABELS = new String[16];
    private static World cachedWorld;
    private static long nextScan;
    private static int cachedRadius;
    private static int cachedSource;
    private static List<Cell> cells = List.of();

    static {
        for (int i = 0; i < LABELS.length; i++) LABELS[i] = Integer.toString(i);
    }

    private LightLevelOverlay() {}

    public static void initialize() {
        LightLevelConfig.INSTANCE.preload();
    }

    public static void render(float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (!LightLevelConfig.getEnabled() || mc.world == null || mc.player == null) {
            if (cachedWorld != null) {
                cachedWorld = null;
                cells = List.of();
                nextScan = 0;
            }
            return;
        }
        if (mc.options.hideGui) return;
        int radius = Math.max(8, Math.min(32, LightLevelConfig.getRadius()));
        int source = LightLevelConfig.getLightSource();
        long now = System.nanoTime();
        if (mc.world != cachedWorld || radius != cachedRadius || source != cachedSource || now >= nextScan) {
            cachedWorld = mc.world;
            cachedRadius = radius;
            cachedSource = source;
            nextScan = now + SCAN_INTERVAL_NS;
            List<Cell> next = scan(mc.world, new BlockPos(mc.player), radius, source != 0);
            if (!next.equals(cells)) cells = List.copyOf(next);
        }
        if (cells.isEmpty()) return;
        Entity camera = mc.camera;
        if (camera == null) return;
        double x = camera.prevX + (camera.x - camera.prevX) * tickDelta;
        double y = camera.prevY + (camera.y - camera.prevY) * tickDelta;
        double z = camera.prevZ + (camera.z - camera.prevZ) * tickDelta;

        GlStateManager.pushMatrix();
        GlStateManager.translated(-x, -y, -z);
        GlStateManager.enableDepthTest();
        GlStateManager.depthMask(false);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);
        boolean previousFontScope = FontPreferences.beginModText();
        try {
            if (LightLevelConfig.getOutlines()) drawOutlines();
            GlStateManager.enableTexture();
            for (Cell cell : cells) drawNumber(mc.textRenderer, cell, camera.yaw);
        } finally {
            FontPreferences.endModText(previousFontScope);
            GlStateManager.color4f(1f, 1f, 1f, 1f);
            GlStateManager.enableTexture();
            GlStateManager.depthMask(true);
            GlStateManager.enableCull();
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }
    }

    private static List<Cell> scan(World world, BlockPos center, int radius, boolean includeSky) {
        ArrayList<Cell> result = new ArrayList<>();
        ChunkSource chunks = world.getChunkSource();
        int skyDarkness = includeSky ? world.calculateAmbientDarkness(1f) : 0;
        int lowY = Math.max(0, center.getY() - 8);
        int highY = Math.min(254, center.getY() + 8);
        int firstChunkX = (center.getX() - radius) >> 4;
        int lastChunkX = (center.getX() + radius) >> 4;
        int firstChunkZ = (center.getZ() - radius) >> 4;
        int lastChunkZ = (center.getZ() + radius) >> 4;
        for (int chunkX = firstChunkX; chunkX <= lastChunkX; chunkX++) {
            for (int chunkZ = firstChunkZ; chunkZ <= lastChunkZ; chunkZ++) {
                if (!chunks.hasChunk(chunkX, chunkZ)) continue;
                WorldChunk chunk = chunks.getChunk(chunkX, chunkZ);
                if (chunk == null || chunk instanceof EmptyChunk || !chunk.loaded) continue;
                int minX = Math.max(chunkX << 4, center.getX() - radius);
                int maxX = Math.min((chunkX << 4) + 15, center.getX() + radius);
                int minZ = Math.max(chunkZ << 4, center.getZ() - radius);
                int maxZ = Math.min((chunkZ << 4) + 15, center.getZ() + radius);
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        int dx = x - center.getX();
                        int dz = z - center.getZ();
                        if (dx * dx + dz * dz > radius * radius) continue;
                        for (int y = lowY; y <= highY; y++) {
                            BlockPos pos = new BlockPos(x, y, z);
                            BlockState state = LegacyWorld.state(chunk, pos);
                            Block block = state.getBlock();
                            if (!block.getMaterial().isSolid() || block.getMaterial().isLiquid()) continue;
                            BlockPos above = pos.up();
                            BlockState aboveState = LegacyWorld.state(chunk, above);
                            Block aboveBlock = aboveState.getBlock();
                            if (aboveBlock.getMaterial().isLiquid() || aboveBlock.isSolid()) continue;
                            LegacyWorld.shape(block, world, pos);
                            Box shape = LegacyWorld.collision(block, world, pos);
                            if (shape == null || shape.maxX - shape.minX < 0.99 || shape.maxZ - shape.minZ < 0.99) continue;
                            Box covering = LegacyWorld.collision(aboveBlock, world, above);
                            if (covering != null && covering.minY <= shape.maxY + 0.01) continue;
                            int light = storedLight(world, chunk, above, includeSky, skyDarkness);
                            result.add(new Cell(x, z, shape.maxY, Math.max(0, Math.min(15, light))));
                        }
                    }
                }
            }
        }
        return result;
    }

    private static int storedLight(World world, WorldChunk chunk, BlockPos pos, boolean includeSky, int skyDarkness) {
        WorldChunkSection section = chunk.getSections()[pos.getY() >> 4];
        int x = pos.getX() & 15;
        int y = pos.getY() & 15;
        int z = pos.getZ() & 15;
        int block = section == null ? 0 : section.getBlockLight(x, y, z);
        if (!includeSky || world.dimension.noSky) return block;
        int sky = section == null ? (chunk.hasSkyAccessAt(x, pos.getY(), z) ? 15 : 0) : section.getSkyLight(x, y, z);
        return Math.max(block, Math.max(0, sky - skyDarkness));
    }

    private static void drawOutlines() {
        GlStateManager.disableTexture();
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder vertices = tessellator.getBuffer();
        vertices.begin(1, DefaultVertexFormat.POSITION_COLOR);
        for (Cell cell : cells) {
            if (cell.light > 7) continue;
            double y = cell.top + 0.01;
            double left = cell.x + 0.025;
            double right = cell.x + 0.975;
            double near = cell.z + 0.025;
            double far = cell.z + 0.975;
            line(vertices, left, y, near, right, y, near);
            line(vertices, right, y, near, right, y, far);
            line(vertices, right, y, far, left, y, far);
            line(vertices, left, y, far, left, y, near);
        }
        tessellator.end();
    }

    private static void line(BufferBuilder vertices, double x1, double y1, double z1, double x2, double y2, double z2) {
        vertices.vertex(x1, y1, z1).color(255, 85, 85, 220).nextVertex();
        vertices.vertex(x2, y2, z2).color(255, 85, 85, 220).nextVertex();
    }

    private static void drawNumber(TextRenderer font, Cell cell, float yaw) {
        String label = LABELS[cell.light];
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translated(cell.x + 0.5, cell.top + 0.015, cell.z + 0.5);
            GlStateManager.rotatef(180f - yaw, 0f, 1f, 0f);
            GlStateManager.rotatef(90f, 1f, 0f, 0f);
            GlStateManager.scalef(0.04f, 0.04f, 0.04f);
            font.drawWithShadow(label, -font.getWidth(label) / 2, -font.fontHeight / 2,
                    cell.light <= 7 ? 0xFFFF5555 : 0xFF55FF55);
        } finally {
            GlStateManager.popMatrix();
        }
    }
}
