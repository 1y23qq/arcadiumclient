// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.featuretest;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.game.inventory.CreativeInventoryScreen;
import net.minecraft.block.Blocks;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.WorldSettings;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.world.gen.WorldGeneratorType;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

public final class LegacyWholeClientPerformance040Qa {
    private static final boolean ENABLED = Boolean.getBoolean("arcadium.legacyWholeClient040");
    private static final String[] SCENES = {"terrain", "players", "inventory"};
    private static final Map<String, Object> RESULT = new LinkedHashMap<>();
    private static final List<Map<String, Object>> SAMPLES = new ArrayList<>();
    private static final List<LegacyQaPlayer> PLAYERS = new ArrayList<>();
    private static final AtomicBoolean SERVER_REQUESTED = new AtomicBoolean();
    private static final AtomicBoolean SERVER_READY = new AtomicBoolean();
    private static final AtomicReference<Throwable> FAILURE = new AtomicReference<>();
    private static final ThreadMXBean THREAD = ManagementFactory.getThreadMXBean();
    private static final long[] FRAMES = new long[1_000_000];
    private static volatile double height;
    private static volatile int serverObstructions;
    private static int ticks, scene = -1, frameCount, inactiveChecks, suppressedSyntheticSkinRequests;
    private static boolean started, setup, sampling, done;
    private static long startDraws, startNativeBatches;
    private static long launchTime, phaseStart, previousFrame, startCpu, startGc, startCompilation, startedMillis;

    private LegacyWholeClientPerformance040Qa() { }
    public static boolean enabled() { return ENABLED; }

    public static boolean suppressSyntheticSkinRequest(GameProfile profile) {
        if (!ENABLED || profile == null || profile.getId() == null || profile.getId().getMostSignificantBits() != 0) return false;
        long index = profile.getId().getLeastSignificantBits() - 20_000;
        if (index < 0 || index >= 100 || !("Fixture" + index).equals(profile.getName())) return false;
        suppressedSyntheticSkinRequests++;
        return true;
    }

    public static void tick() {
        if (!ENABLED || done) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (++ticks == 1) {
                launchTime = System.nanoTime();
                require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()), "An isolated offline session is required");
                require(Path.of(System.getProperty("arcadium.legacyWholeClient040.gameDir")).toRealPath().equals(mc.gameDir.toPath().toRealPath()), "Unexpected game directory");
                mc.options.pauseOnUnfocus = false;
                mc.options.viewDistance = 8;
                mc.options.guiScale = 2;
                mc.options.fpsLimit = 260;
                mc.options.vsync = false;
                Display.setVSyncEnabled(false);
                mc.options.fov = 70;
                mc.options.fancyGraphics = true;
                mc.options.ambientOcclusion = 2;
                mc.options.renderClouds = true;
                mc.options.particles = 0;
                mc.options.gamma = 0f;
                mc.options.perspective = 0;
                RESULT.put("variant", System.getProperty("arcadium.legacyWholeClient040.variant"));
                RESULT.put("gpu", GL11.glGetString(GL11.GL_RENDERER));
                RESULT.put("java", System.getProperty("java.version"));
                RESULT.put("samples", SAMPLES);
                RESULT.put("minecraft", "1.7.10");
                RESULT.put("fontBatchEnabled", Class.forName("io.github.arcadiumclient.performance.LegacyPerformance").getMethod("fontBatchEnabled").invoke(null));
                RESULT.put("terrainBatchEnabled", Class.forName("io.github.arcadiumclient.performance.terrain.TerrainRenderer").getMethod("enabled").invoke(null));
                RESULT.put("nativeYieldRestored", Boolean.getBoolean("arcadium.legacyPerformance.keepFrameYield"));
                THREAD.setThreadCpuTimeEnabled(true);
                write(mc, "loading");
            }
            require(System.nanoTime() - launchTime < 300_000_000_000L, "Performance fixture timed out");
            if (!started && ticks > 30) {
                started = true;
                mc.startGame("benchmark-world", "Arcadium Performance Fixture", new WorldSettings(71,
                    WorldSettings.getGameModeById(1), false, false, WorldGeneratorType.FLAT).enableCommands());
            }
            if (FAILURE.get() != null) throw new IllegalStateException("World setup failed", FAILURE.get());
            if (mc.world == null || mc.player == null) return;
            if (!setup) {
                setup = true;
                SERVER_REQUESTED.set(true);
            }
            if (!SERVER_READY.get()) return;
            java.util.Arrays.fill(mc.player.inventory.items, null);
            pin(mc);
            if (scene < 0) {
                scene = 0;
                beginScene(mc);
            }
            for (int i = 0; i < PLAYERS.size(); i++) {
                LegacyQaPlayer player = PLAYERS.get(i);
                player.setPositionAndAngles(8.5 + (i % 10 - 4.5) * 1.5, height, 18.5 + (i / 10) * 1.5, 180, 0);
                player.yaw = player.lastYaw = 180;
            }
        } catch (Throwable failure) { fail(failure); }
    }

    public static void serverTick(MinecraftServer server) {
        if (!ENABLED || !SERVER_REQUESTED.get() || SERVER_READY.get() || FAILURE.get() != null || server.getPlayerManager() == null || server.getPlayerManager().players.isEmpty()) return;
        try {
            var world = server.getWorld(0);
            world.getGameRules().set("doMobSpawning", "false");
            world.getGameRules().set("doDaylightCycle", "false");
            world.getGameRules().set("randomTickSpeed", "0");
            world.setTimeOfDay(6000);
            world.setAllowedMobSpawns(false, false);
            height = 4;
            for (int x = -10; x <= 10; x++) for (int z = -10; z <= 10; z++) {
                world.getBlock(x * 16, 4, z * 16);
            }
            for (int x = -48; x <= 64; x += 8) for (int z = 48; z <= 112; z += 8) {
                int top = 8 + Math.floorMod(x / 8 + z / 8, 5) * 2;
                for (int dx = 0; dx < 5; dx++) for (int dz = 0; dz < 5; dz++) for (int y = 4; y <= top; y++) {
                    world.setBlockWithMetadata(x + dx, y, z + dz, Blocks.STONE, 0, 2);
                }
            }
            for (int x = -14; x <= 31; x++) for (int z = 1; z <= 45; z++) for (int y = 4; y <= 30; y++) {
                if (world.getBlock(x, y, z) != Blocks.AIR) serverObstructions++;
            }
            require(serverObstructions == 0, "The saved benchmark plaza contains obstructions: " + serverObstructions);
            for (var entity : new ArrayList<>(world.entities)) if (!(entity instanceof PlayerEntity)) world.removeEntity((net.minecraft.entity.Entity) entity);
            var player = (ServerPlayerEntity) server.getPlayerManager().players.get(0);
            java.util.Arrays.fill(player.inventory.items, null);
            java.util.Arrays.fill(player.inventory.armor, null);
            player.teleport(8.5, height + 4.5, 8.5);
            SERVER_READY.set(true);
        } catch (Throwable failure) { FAILURE.set(failure); }
    }

    private static void pin(Minecraft mc) {
        float pitch = 15;
        mc.player.setPositionAndAngles(8.5, height + 4.5, 8.5, 0, pitch);
        mc.player.yaw = mc.player.lastYaw = 0;
        mc.player.pitch = mc.player.lastPitch = pitch;
        mc.player.abilities.flying = true;
    }

    private static void beginScene(Minecraft mc) throws Exception {
        if (scene == 1) {
            for (int i = 0; i < 100; i++) {
                LegacyQaPlayer player = new LegacyQaPlayer(mc.world, new GameProfile(new UUID(0, 20_000 + i), "Fixture" + i));
                player.inventory.armor = new ItemStack[]{new ItemStack(Items.DIAMOND_BOOTS), new ItemStack(Items.DIAMOND_LEGGINGS),
                    new ItemStack(Items.DIAMOND_CHESTPLATE), new ItemStack(Items.DIAMOND_HELMET)};
                player.setPositionAndAngles(8.5 + (i % 10 - 4.5) * 1.5, height, 18.5 + (i / 10) * 1.5, 180, 0);
                PLAYERS.add(player);
                mc.world.forceEntity(20_000 + i, player);
            }
        } else if (scene == 2) {
            for (LegacyQaPlayer player : PLAYERS) mc.world.removeEntity(player);
            PLAYERS.clear();
            CreativeInventoryScreen screen = new CreativeInventoryScreen(mc.player);
            mc.player.menu = screen.menu;
            mc.openScreen(screen);
        } else mc.openScreen(null);
        phaseStart = System.nanoTime();
        previousFrame = 0;
        sampling = false;
        frameCount = inactiveChecks = 0;
        write(mc, "warmup-" + SCENES[scene]);
    }

    public static void afterFrame() {
        if (!ENABLED || scene < 0) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (done) {
                if (mc.getServer() == null || mc.getServer().hasStopped()) mc.stop();
                return;
            }
            long now = System.nanoTime();
            double warmup = Double.parseDouble(System.getProperty("arcadium.legacyWholeClient040.warmup", "15"));
            if (scene != 0) warmup = Math.min(warmup, 12);
            if (!sampling) {
                if (now - phaseStart < warmup * 1_000_000_000L) return;
                int clientObstructions = 0;
                for (int x = -14; x <= 31; x++) for (int z = 1; z <= 45; z++) for (int y = 4; y <= 30; y++) {
                    if (mc.world.getBlock(x, y, z) != Blocks.AIR) clientObstructions++;
                }
                RESULT.put("serverObstructions", serverObstructions);
                RESULT.put("clientObstructions", clientObstructions);
                require(clientObstructions == 0, "The benchmark plaza is not clear on the client: " + clientObstructions);
                sampling = true;
                previousFrame = phaseStart = now;
                startedMillis = System.currentTimeMillis();
                startCpu = THREAD.getCurrentThreadCpuTime();
                startGc = gcTime();
                startCompilation = ManagementFactory.getCompilationMXBean().getTotalCompilationTime();
                startDraws = terrainCounter("drawCalls"); startNativeBatches = terrainCounter("nativeBatches");
                write(mc, "measure-" + SCENES[scene]);
                previousFrame = System.nanoTime();
                return;
            }
            require(frameCount < FRAMES.length, "Frame sample buffer exceeded");
            FRAMES[frameCount++] = now - previousFrame;
            previousFrame = now;
            if ((frameCount & 63) == 0 && !Display.isActive()) inactiveChecks++;
            double duration = Double.parseDouble(System.getProperty("arcadium.legacyWholeClient040.seconds", "12"));
            if (now - phaseStart < duration * 1_000_000_000L) return;
            long cpu = THREAD.getCurrentThreadCpuTime() - startCpu;
            long compilation = ManagementFactory.getCompilationMXBean().getTotalCompilationTime() - startCompilation;
            Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("scene", SCENES[scene]); sample.put("frames", frameCount);
            sample.put("startedMillis", startedMillis); sample.put("endedMillis", System.currentTimeMillis());
            sample.put("renderThreadCpuNanos", cpu); sample.put("gcMillis", gcTime() - startGc);
            sample.put("jitCompilationMillis", compilation); sample.put("inactiveChecks", inactiveChecks);
            sample.put("batchedTerrainDraws", terrainCounter("drawCalls") - startDraws);
            sample.put("nativeTerrainBatches", terrainCounter("nativeBatches") - startNativeBatches);
            sample.put("width", mc.width); sample.put("height", mc.height); sample.put("viewDistance", mc.options.viewDistance);
            sample.put("vsync", mc.options.vsync); sample.put("fpsLimit", mc.options.fpsLimit); sample.put("fov", mc.options.fov);
            sample.put("chunkDebug", mc.getRenderChunkDebugInfo());
            sample.put("entityDebug", mc.getRenderEntityDebugInfo());
            sample.put("fixturePlayers", PLAYERS.size()); sample.put("worldSeed", mc.getServer().getWorld(0).getSeed());
            sample.put("clientWorldSeed", mc.world.getSeed());
            sample.put("feetPosition", List.of(mc.player.x, mc.player.shape.minY, mc.player.z, (double) mc.player.yaw, (double) mc.player.pitch));
            sample.put("syntheticSkinRequestsSuppressed", suppressedSyntheticSkinRequests);
            sample.put("nonEmptyInventorySlots", java.util.Arrays.stream(mc.player.inventory.items).filter(java.util.Objects::nonNull).count());
            sample.put("camera", List.of(mc.player.x, mc.player.y, mc.player.z, (double) mc.player.yaw, (double) mc.player.pitch));
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(output(mc).resolve(SCENES[scene] + ".frames"))))) {
                for (int i = 0; i < frameCount; i++) output.writeLong(FRAMES[i]);
            }
            Screenshot.take(mc.gameDir, "performance-" + SCENES[scene] + ".png", mc.width, mc.height, mc.getRenderTarget());
            SAMPLES.add(sample);
            if (++scene >= SCENES.length) finish(mc); else beginScene(mc);
        } catch (Throwable failure) { fail(failure); }
    }

    private static long terrainCounter(String name) throws Exception {
        return ((Number) Class.forName("io.github.arcadiumclient.performance.terrain.TerrainRenderer").getMethod(name).invoke(null)).longValue();
    }

    private static long gcTime() {
        long total = 0;
        for (var bean : ManagementFactory.getGarbageCollectorMXBeans()) if (bean.getCollectionTime() >= 0) total += bean.getCollectionTime();
        return total;
    }

    private static Path output(Minecraft mc) { return mc.gameDir.toPath().resolve("performance"); }
    private static void write(Minecraft mc, String phase) throws Exception {
        RESULT.put("phase", phase);
        RESULT.put("height", height);
        Files.createDirectories(output(mc));
        Path pending = output(mc).resolve("result.pending.json");
        Files.writeString(pending, new GsonBuilder().setPrettyPrinting().create().toJson(RESULT));
        for (int attempt = 0; ; attempt++) {
            try {
                Files.move(pending, output(mc).resolve("result.json"), StandardCopyOption.REPLACE_EXISTING);
                break;
            } catch (java.nio.file.FileSystemException locked) {
                if (attempt >= 49) throw locked;
                Thread.sleep(5);
            }
        }
        System.out.println("Arcadium performance: " + phase);
    }

    private static void finish(Minecraft mc) throws Exception {
        RESULT.put("passed", true);
        write(mc, "complete");
        done = true;
        mc.getServer().stop();
    }

    private static void fail(Throwable failure) {
        if (done) return;
        done = true;
        failure.printStackTrace();
        try {
            RESULT.put("passed", false);
            RESULT.put("failure", failure.toString());
            Minecraft mc = Minecraft.getInstance();
            write(mc, "failed");
            mc.stop();
        } catch (Exception secondary) { secondary.printStackTrace(); }
    }

    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
