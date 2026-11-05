// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.runtime.client.ClientPreferences;
import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import io.github.arcadiumclient.runtime.client.optimizations.GpuTerrainCommands;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.Future;
import javax.imageio.ImageIO;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

public final class TerrainScene072Qa {
    private static int ticks, wait, phase, view;
    private static boolean done, capture;
    private static Future<?> setup;
    private static final Map<String, Object> report = new LinkedHashMap<>();
    private static final List<Map<String, Object>> comparisons = new ArrayList<>();
    private static final double[][] VIEWS = {{0, 12, -12, 0, 45}, {19, 12, -12, 25, 45}, {-18, 12, 17, -80, 45}};
    private TerrainScene072Qa() {}

    public static void tick() {
        if (done || System.getProperty("arcadium.terrainScene072.output") == null) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (++ticks > 1800) throw new IllegalStateException("Terrain scene QA timed out at phase " + phase);
            wait++;
            if (phase == 0 && ticks > 80 && mc.screen != null) {
                if (!mc.getSession().getUsername().equals("ArcadiumTest") || !mc.getSession().getAccessToken().equals("0") || mc.world != null)
                    throw new IllegalStateException("Use the isolated offline fixture");
                ClientPreferences.setSetupComplete(true);
                mc.options.pauseOnUnfocus = false; mc.options.vsync = false; mc.options.viewDistance = 4;
                mc.options.fpsLimit = 120; mc.options.cloudRenderMode = 0; mc.options.perspective = 0;
                mc.options.viewBobbing = false; mc.options.hideGui = true; mc.options.fov = 70;
                mc.options.difficulty = Difficulty.PEACEFUL;
                Display.setVSyncEnabled(false);
                org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps = false;
                BetaOptimizationsConfig.setGpuModelTransforms(false); BetaOptimizationsConfig.setGpuPlayerPipeline(false);
                BetaOptimizationsConfig.setGpuNameTags(false); BetaOptimizationsConfig.setGpuEquipmentPipeline(false);
                BetaOptimizationsConfig.setGpuParticles(false); BetaOptimizationsConfig.setWorldRenderScale(false);
                setEnabled(false);
                mc.startGame("arcadium-terrain072", "Terrain 0.7.2 QA", new WorldSettings(7209, WorldSettings.GameMode.CREATIVE, false, false, WorldGeneratorType.FLAT).enableCommands());
                phase = 1; wait = 0;
            } else if (phase == 1 && mc.world != null && mc.player != null && wait > 70) {
                if (mc.getServer() == null) throw new IllegalStateException("Terrain fixture must use the local integrated server");
                mc.openScreen(null);
                setup = mc.getServer().executeTask(() -> {
                    var world = mc.getServer().getWorld(0);
                    world.getGameRules().set("doDaylightCycle", "false");
                    world.getGameRules().set("doMobSpawning", "false");
                    world.getGameRules().set("randomTickSpeed", "0");
                    world.setTimeOfDay(6000);
                    for (var entity : new ArrayList<>(world.entities)) if (!(entity instanceof PlayerEntity)) world.removeEntityNow(entity);
                    for (int x = -32; x <= 32; x += 4) for (int z = -4; z <= 40; z += 4) {
                        int height = 1 + Math.floorMod(x * 17 + z * 11, 5);
                        for (int y = 4; y < 4 + height; y++) world.setBlockState(new BlockPos(x, y, z), Blocks.WOOL.getStateFromMetadata(Math.floorMod(x + z, 16)), 3);
                        world.setBlockState(new BlockPos(x + 1, 4, z), Blocks.FENCE.defaultState(), 3);
                        world.setBlockState(new BlockPos(x + 2, 4, z), Blocks.LEAVES.getStateFromMetadata(0), 3);
                        world.setBlockState(new BlockPos(x + 3, 4, z), Blocks.STAINED_GLASS.getStateFromMetadata(5), 3);
                    }
                });
                phase = 2; wait = 0;
            } else if (phase == 2 && setup.isDone() && wait > 160) {
                setup.get(); phase = 3; wait = 0;
                report.put("renderer", GL11.glGetString(GL11.GL_RENDERER));
                report.put("version", GL11.glGetString(GL11.GL_VERSION));
                report.put("settings", Map.of("viewDistance", 4, "fpsLimit", 120, "vsync", false, "fov", 70, "worldScale", false,
                        "gpuPlayers", false, "gpuNames", false, "gpuParticles", false, "gpuModelTransforms", false));
            } else if ((phase == 3 || phase == 4) && wait > 30) capture = true;
            else if (phase == 5 && setup.isDone() && wait > 100) { setup.get(); phase = 3; wait = 0; }
            else if (phase == 6 && wait > 10 && completePng(mc.gameDir.toPath().resolve("screenshots/terrain072-" + view + "-gpu.png"))) capture = true;
            if (mc.world != null && mc.player != null && phase >= 2) freeze(mc);
        } catch (Throwable error) { finish(error); }
    }

    public static void frame() {
        if (done || !capture) return;
        capture = false;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (GL11.glGetError() != GL11.GL_NO_ERROR) throw new IllegalStateException("GL error during terrain scene");
            String mode = phase == 3 ? "baseline" : "gpu";
            String name = "terrain072-" + view + "-" + mode + ".png";
            if (phase == 3 || phase == 4) Screenshot.take(mc.gameDir, name, mc.width, mc.height, mc.getRenderTarget());
            if (phase == 3) {
                setEnabled(true); phase = 4; wait = 0;
            } else if (phase == 4) {
                phase = 6; wait = 0;
            } else {
                if (GpuTerrainCommands.regionDraws == 0 || GpuTerrainCommands.dispatches == 0) throw new IllegalStateException("GPU terrain was not used: " + GpuTerrainCommands.status());
                Path folder = mc.gameDir.toPath().resolve("screenshots");
                comparisons.add(compare(folder.resolve("terrain072-" + view + "-baseline.png"), folder.resolve(name)));
                if (++view == VIEWS.length) {
                    report.put("comparisons", comparisons); report.put("regionDraws", GpuTerrainCommands.regionDraws);
                    report.put("dispatches", GpuTerrainCommands.dispatches); report.put("reuses", GpuTerrainCommands.commandsReused);
                    report.put("templateUploads", GpuTerrainCommands.templateUploads); report.put("visibilityUploads", GpuTerrainCommands.visibilityUploads);
                    report.put("gpuBytesBeforeReset", GpuTerrainCommands.residentBytes());
                    GpuTerrainCommands.reset();
                    if (GpuTerrainCommands.residentBytes() != 0) throw new IllegalStateException("Terrain GPU buffers were not released");
                    report.put("allBuffersReleased", true); finish(null); return;
                }
                setEnabled(false); phase = 5; wait = 0;
                int changed = view;
                setup = mc.getServer().executeTask(() -> mc.getServer().getWorld(0).setBlockState(new BlockPos(changed * 4, 8, 12), Blocks.GOLD_BLOCK.defaultState(), 3));
            }
        } catch (Throwable error) { finish(error); }
    }

    private static void freeze(Minecraft mc) {
        double[] camera = VIEWS[Math.min(view, VIEWS.length - 1)];
        mc.player.abilities.flying = true; mc.player.setInvisible(true);
        mc.player.updatePositionAndAngles(camera[0], camera[1], camera[2], (float) camera[3], (float) camera[4]);
        mc.player.lastX = mc.player.prevX = mc.player.x; mc.player.lastY = mc.player.prevY = mc.player.y; mc.player.lastZ = mc.player.prevZ = mc.player.z;
        mc.player.lastYaw = mc.player.yaw; mc.player.lastPitch = mc.player.pitch;
        mc.player.velocityX = mc.player.velocityY = mc.player.velocityZ = 0;
        mc.world.setTimeOfDay(6000);
    }

    private static void setEnabled(boolean value) throws ReflectiveOperationException {
        GpuTerrainCommands.enabled = value;
        try { BetaOptimizationsConfig.class.getMethod("setGpuTerrainCommands", boolean.class).invoke(null, value); }
        catch (NoSuchMethodException ignored) {}
    }

    private static Map<String, Object> compare(Path first, Path second) throws Exception {
        BufferedImage a = ImageIO.read(first.toFile()), b = ImageIO.read(second.toFile());
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) throw new IllegalStateException("Terrain screenshots resized unexpectedly");
        int checked = 0, changed = 0, maximum = 0;
        for (int y = a.getHeight() / 4; y < a.getHeight() * 17 / 20; y++) for (int x = a.getWidth() / 4; x < a.getWidth() * 3 / 4; x++) {
            if (Math.abs(x - a.getWidth() / 2) < 16 && Math.abs(y - a.getHeight() / 2) < 16) continue;
            int left = a.getRGB(x, y), right = b.getRGB(x, y), difference = 0;
            for (int shift = 0; shift < 24; shift += 8) difference = Math.max(difference, Math.abs((left >> shift & 255) - (right >> shift & 255)));
            checked++; maximum = Math.max(maximum, difference); if (difference > 1) changed++;
        }
        if (changed > 3) throw new IllegalStateException("Terrain geometry differs in " + changed + "/" + checked + " pixels (max channel error " + maximum + ")");
        return Map.of("baseline", first.getFileName().toString(), "gpu", second.getFileName().toString(), "checkedPixels", checked, "changedPixels", changed, "maximumChannelError", maximum);
    }

    private static boolean completePng(Path file) throws Exception {
        if (!Files.isRegularFile(file) || Files.size(file) < 12) return false;
        try (var channel = Files.newByteChannel(file)) {
            ByteBuffer end = ByteBuffer.allocate(12); channel.position(channel.size() - 12);
            while (end.hasRemaining() && channel.read(end) > 0) {}
            return end.position() == 12 && end.getInt(0) == 0 && end.getInt(4) == 0x49454e44;
        }
    }

    private static void finish(Throwable error) {
        if (done) return; done = true;
        report.put("passed", error == null);
        if (error != null) { report.put("failure", error.toString()); error.printStackTrace(); }
        try {
            Path file = Path.of(System.getProperty("arcadium.terrainScene072.output")); Files.createDirectories(file.getParent());
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Exception failure) { failure.printStackTrace(); }
        Minecraft.getInstance().stop();
    }
}
