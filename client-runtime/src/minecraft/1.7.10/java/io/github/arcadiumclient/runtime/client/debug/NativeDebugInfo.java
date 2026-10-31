// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.debug;

import java.util.*;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

public final class NativeDebugInfo {
    private NativeDebugInfo() { }
    public static boolean nativeFpsChart(Minecraft mc) { return false; }
    public static int ping(Minecraft mc) {
        if(mc.player==null || mc.getNetworkHandler()==null)return -1;
        for(Object entry:mc.getNetworkHandler().onlinePlayers)if(entry instanceof net.minecraft.client.network.PlayerInfo player && player.name.equals(mc.player.getName()))return player.ping;
        return -1;
    }
    public static Map<String, String> collect(Minecraft mc, DebugInfoSource ignored) {
        Map<String, String> values = new HashMap<>();
        var camera = mc.camera; if (camera == null) camera = mc.player;
        int x = (int)Math.floor(camera.x), y = (int)Math.floor(camera.y), z = (int)Math.floor(camera.z);
        values.put("minecraft", "1.7.10 / Arcadium / Forge");
        values.put("fps", mc.fpsDebugInfo);
        values.put("render_distance", mc.options.viewDistance + " chunks");
        values.put("graphics", mc.options.fancyGraphics ? "Fancy" : "Fast");
        values.put("clouds", mc.options.renderClouds ? "On" : "Off");
        values.put("player_coords", String.format(Locale.ROOT,"%.3f / %.5f / %.3f", camera.x,camera.y,camera.z));
        values.put("block_coords", x + " / " + y + " / " + z);
        values.put("chunk_coords", (x >> 4) + " / " + (y >> 4) + " / " + (z >> 4));
        values.put("chunk_relative_coords", (x & 15) + " / " + (y & 15) + " / " + (z & 15));
        values.put("rotation", String.format(Locale.ROOT,"%.1f / %.1f",camera.yaw,camera.pitch));
        values.put("facing", switch ((int)Math.floor(camera.yaw * 4 / 360 + 0.5) & 3) { case 0 -> "South (+Z)"; case 1 -> "West (-X)"; case 2 -> "North (-Z)"; default -> "East (+X)"; });
        values.put("velocity", String.format(Locale.ROOT,"%.3f / %.3f / %.3f",camera.velocityX*20,camera.velocityY*20,camera.velocityZ*20));
        values.put("absolute_velocity", String.format(Locale.ROOT,"%.3f m/s",Math.sqrt(camera.velocityX*camera.velocityX+camera.velocityY*camera.velocityY+camera.velocityZ*camera.velocityZ)*20));
        values.put("horizontal_velocity", String.format(Locale.ROOT,"%.3f m/s",Math.hypot(camera.velocityX,camera.velocityZ)*20));
        values.put("biome", mc.world.getBiome(x,z).name);
        values.put("light", Integer.toString(mc.world.getActualLight(x,y,z)));
        values.put("dimension", Integer.toString(mc.player.dimension));
        values.put("highest_block", Integer.toString(mc.world.getHeight(x,z)));
        values.put("day_ticks", Long.toString(mc.world.getTimeOfDay()%24000));
        values.put("days_played", Long.toString(mc.world.getTimeOfDay()/24000));
        values.put("chunk_sections", mc.worldRenderer.getChunkDebugInfo());
        values.put("client_chunk_cache", mc.world.getChunkSourceDebugInfo());
        values.put("entities", mc.worldRenderer.getEntityDebugInfo());
        values.put("particles", mc.particleManager.getDebugInfo());
        if (mc.targetEntity != null) values.put("targeted_entity",mc.targetEntity.getName());
        Runtime runtime=Runtime.getRuntime(); long max=runtime.maxMemory(), total=runtime.totalMemory(), used=total-runtime.freeMemory();
        values.put("java_version",System.getProperty("java.version") + " " + System.getProperty("os.arch"));
        values.put("memory_usage",String.format(Locale.ROOT,"%d%% %d/%d MB",used*100/max,used/1048576,max/1048576));
        values.put("allocated_memory",String.format(Locale.ROOT,"%d%% %d MB",total*100/max,total/1048576));
        values.put("cpu", System.getenv().getOrDefault("PROCESSOR_IDENTIFIER",Runtime.getRuntime().availableProcessors()+" logical processors ("+System.getProperty("os.arch")+")"));
        values.put("display",Display.getWidth()+" x "+Display.getHeight()+" ("+GL11.glGetString(GL11.GL_VENDOR)+")");
        values.put("gpu",GL11.glGetString(GL11.GL_RENDERER));
        values.put("gpu_driver",GL11.glGetString(GL11.GL_VERSION));
        values.put("opengl_version","OpenGL");
        values.put("pie_graph_new",mc.options.debugProfilerEnabled?"Visible (Shift + F3)":"Hidden (Shift + F3)");
        values.put("help","F3 + H: advanced tooltips; F3 + B: hitboxes; F3 + M: Custom F3");
        return values;
    }
}
