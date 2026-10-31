// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.debug;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.Block;
import io.github.arcadiumclient.shared.debug.LegacyDebugParser;

public final class NativeDebugInfo {
    private NativeDebugInfo() { }
    public static int ping(Minecraft mc) { var connection=mc.getNetworkHandler();var player=connection==null||mc.player==null?null:connection.getOnlinePlayer(mc.player.getUuid());return player==null?-1:player.getPing(); }
    public static boolean nativeFpsChart(Minecraft mc) { return mc.options.debugTpsEnabled; }
    public static Map<String, String> collect(Minecraft mc, DebugInfoSource source) {
        Map<String, String> extra = new HashMap<>();
        extra.put("render_distance", mc.options.viewDistance + " chunks");
        extra.put("graphics", mc.options.fancyGraphics ? "Fancy" : "Fast");
        extra.put("clouds", switch (mc.options.getCloudRenderMode()) { case 0 -> "Off"; case 1 -> "Fast"; default -> "Fancy"; });
        extra.put("pie_graph_new", mc.options.debugProfilerEnabled ? "Visible (Shift + F3)" : "Hidden (Shift + F3)");
        extra.put("fps_tps_new", mc.options.debugTpsEnabled ? "Visible (Alt + F3)" : "Hidden (Alt + F3)");
        extra.put("help", "F3 + H: advanced tooltips; F3 + B: hitboxes; F3 + M: Custom F3");
        if (mc.isIntegratedServerRunning() && mc.getServer() != null) {
            double ms = Arrays.stream(mc.getServer().averageTickTimes).average().orElse(0) / 1_000_000;
            extra.put("server_tick", String.format(Locale.ROOT, "%.1f ms", ms));
        }
        if (!mc.player.hasReducedDebugInfo() && !mc.options.reducedDebugInfo) {
            var camera = mc.getCamera();
            extra.put("dimension", Integer.toString(mc.player.dimension));
            extra.put("rotation", String.format(Locale.ROOT, "%.1f / %.1f", camera.yaw, camera.pitch));
            extra.put("velocity", String.format(Locale.ROOT, "%.3f / %.3f / %.3f", camera.velocityX * 20, camera.velocityY * 20, camera.velocityZ * 20));
            extra.put("absolute_velocity", String.format(Locale.ROOT, "%.3f m/s", Math.sqrt(camera.velocityX * camera.velocityX + camera.velocityY * camera.velocityY + camera.velocityZ * camera.velocityZ) * 20));
            extra.put("horizontal_velocity", String.format(Locale.ROOT, "%.3f m/s", Math.hypot(camera.velocityX, camera.velocityZ) * 20));
            extra.put("day_ticks", Long.toString(mc.world.getTimeOfDay() % 24000));
            extra.put("days_played", Long.toString(mc.world.getTimeOfDay() / 24000));
            BlockPos pos = new BlockPos(camera.x, camera.y, camera.z);
            extra.put("highest_block", Integer.toString(mc.world.getHeight(pos).getY()));
            if (mc.isIntegratedServerRunning()) {
                int x = pos.getX() >> 4, z = pos.getZ() >> 4; long seed = mc.world.getSeed();
                extra.put("slime_chunk", new Random(seed + (long)(x*x*4987142) + x*5947611L + (long)(z*z)*4392871L + z*389711L ^ 987234911L).nextInt(10) == 0 ? "Yes" : "No");
            } else extra.put("slime_chunk", "Unknown (multiplayer)");
            if (mc.targetEntity != null) extra.put("targeted_entity", mc.targetEntity.getName());
        }
        return LegacyDebugParser.parse(source.arcadium$gameInfo(), source.arcadium$systemInfo(), extra);
    }
}
