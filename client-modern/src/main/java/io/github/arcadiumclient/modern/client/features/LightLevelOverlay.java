// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.features;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LightLayer;

public final class LightLevelOverlay {
    public record Cell(int x, int z, double top, int light) {}
    private static final ArrayList<Cell> working = new ArrayList<>();
    private static List<Cell> cells = List.of();
    private static ClientLevel world;
    private static BlockPos center;
    private static int radius, source, column;
    private static long completedAt;
    private static final net.minecraft.util.FormattedCharSequence[] LABELS = new net.minecraft.util.FormattedCharSequence[16];
    static {
        var font = new FontDescription.Resource(Identifier.fromNamespaceAndPath("arcadium_builtin", "mod"));
        for (int i = 0; i < LABELS.length; i++) LABELS[i] = Component.literal(Integer.toString(i)).withStyle(style -> style.withFont(font)).getVisualOrderText();
    }
    private LightLevelOverlay() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!LightLevelConfig.getEnabled() || mc.level == null || mc.player == null) {
            world = null; center = null; cells = List.of(); working.clear(); return;
        }
        int desired = Math.max(8, Math.min(32, LightLevelConfig.getRadius()));
        BlockPos player = mc.player.blockPosition();
        boolean restart = world != mc.level || radius != desired || source != LightLevelConfig.getLightSource() || center == null;
        if (restart || column < 0 && (center.distSqr(player) > 16 || System.nanoTime() - completedAt > 250_000_000L)) {
            world = mc.level; center = player; radius = desired; source = LightLevelConfig.getLightSource(); column = 0; working.clear();
        }
        if (column < 0) return;
        int diameter = radius * 2 + 1, total = diameter * diameter;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int budget = 192;
        while (column < total && budget-- > 0) {
            int dx = column % diameter - radius, dz = column / diameter - radius;
            column++;
            if (dx * dx + dz * dz > radius * radius) continue;
            int x = center.getX() + dx, z = center.getZ() + dz;
            if (!world.hasChunk(x >> 4, z >> 4)) continue;
            for (int y = Math.min(world.getMaxY(), center.getY() + 8); y >= Math.max(world.getMinY(), center.getY() - 8); y--) {
                at.set(x, y, z);
                var state = world.getBlockState(at);
                if (state.isAir() || !state.getFluidState().isEmpty()) continue;
                var shape = state.getCollisionShape(world, at);
                if (shape.isEmpty()) continue;
                var above = at.above();
                if (!world.getBlockState(above).getCollisionShape(world, above).isEmpty()) continue;
                int light = world.getBrightness(LightLayer.BLOCK, above);
                if (source != 0) light = Math.max(light, world.getBrightness(LightLayer.SKY, above) - world.getSkyDarken());
                working.add(new Cell(x, z, y + shape.max(Direction.Axis.Y) + 0.005, Math.max(0, Math.min(15, light))));
            }
        }
        if (column >= total) { cells = List.copyOf(working); working.clear(); column = -1; completedAt = System.nanoTime(); }
    }

    public static void submit(PoseStack pose, LevelRenderState state, SubmitNodeCollector collector) {
        Minecraft mc = Minecraft.getInstance();
        if (!LightLevelConfig.getEnabled() || mc.options.hideGui || mc.level != world || state.cameraRenderState == null) return;
        var camera = state.cameraRenderState.pos;
        for (Cell cell : cells) {
            int color = cell.light <= 7 ? 0xFFFF5555 : 0xFF55FF55;
            pose.pushPose();
            pose.translate(cell.x + 0.5 - camera.x, cell.top - camera.y, cell.z + 0.5 - camera.z);
            pose.mulPose(Axis.YP.rotationDegrees(180f - mc.gameRenderer.getMainCamera().yRot()));
            pose.mulPose(Axis.XP.rotationDegrees(90));
            pose.scale(0.04f, 0.04f, 0.04f);
            var text = LABELS[cell.light];
            collector.submitText(pose, -mc.font.width(text) / 2f, -mc.font.lineHeight / 2f, text, false, Font.DisplayMode.NORMAL, 0xF000F0, color, 0, 0);
            pose.popPose();
            if (LightLevelConfig.getOutlines() && cell.light <= 7) {
                pose.pushPose();
                pose.translate(cell.x - camera.x, cell.top - camera.y, cell.z - camera.z);
                collector.submitCustomGeometry(pose, RenderTypes.lines(), (matrix, vertices) -> {
                    edge(vertices, matrix, 0.02f, 0.02f, 0.98f, 0.02f);
                    edge(vertices, matrix, 0.98f, 0.02f, 0.98f, 0.98f);
                    edge(vertices, matrix, 0.98f, 0.98f, 0.02f, 0.98f);
                    edge(vertices, matrix, 0.02f, 0.98f, 0.02f, 0.02f);
                });
                pose.popPose();
            }
        }
    }
    private static void edge(com.mojang.blaze3d.vertex.VertexConsumer vertices, PoseStack.Pose pose, float x1, float z1, float x2, float z2) {
        NativeLines.segment(vertices, pose, x1, 0, z1, x2, 0, z2, 0xB4FF5555, 1.5f);
    }
}
