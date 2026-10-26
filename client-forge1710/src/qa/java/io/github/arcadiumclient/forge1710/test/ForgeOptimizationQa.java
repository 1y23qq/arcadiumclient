// SPDX-License-Identifier: MIT
package io.github.arcadiumclient.forge1710.test;

import com.google.gson.GsonBuilder;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import io.github.arcadiumclient.runtime.client.features.NoRender;
import io.github.arcadiumclient.runtime.client.features.NoRenderConfig;
import java.io.DataOutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.ref.WeakReference;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import org.lwjgl.opengl.GL11;
import org.lwjglx.opengl.Display;
import org.polyfrost.evergreenhud.client.hud.FpsHud;
import org.polyfrost.oneconfig.api.hud.v1.Hud;
import org.polyfrost.oneconfig.api.hud.v1.HudManager;
import org.polyfrost.oneconfig.api.hud.v1.Section;
import org.polyfrost.oneconfig.internal.OneConfigConfig;

public final class ForgeOptimizationQa {
    private static final String[] SCENES = {"terrain", "items", "light-updates"};
    private static final int ITEMS = 96;
    private static final long WARMUP = Long.getLong("arcadium.qa.optimization.warmupSeconds", 8L) * 1_000_000_000L;
    private static final long DURATION = Long.getLong("arcadium.qa.optimization.sampleSeconds", 12L) * 1_000_000_000L;
    private final Path output = Path.of(System.getProperty("arcadium.qa.optimization.output"));
    private final LinkedHashMap<String, Object> report = new LinkedHashMap<>();
    private final List<Map<String, Object>> samples = new ArrayList<>();
    private final List<Map<String, Object>> unloads = new ArrayList<>();
    private final List<WeakReference<WorldClient>> worlds = new ArrayList<>();
    private final List<WeakReference<Object>> connections = new ArrayList<>();
    private final List<WeakReference<Object>> servers = new ArrayList<>();
    private final List<Long> frameTimes = new ArrayList<>();
    private final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
    private int stage, frames, scene, reloads, lightTick, lightChanges;
    private long after, deadline, lastFrame, sampleStarted, sampleWallStarted, cpuStarted, gcStarted, gcCountStarted;
    private long started = System.nanoTime();
    private Hud fpsHud;
    private boolean sampling, done;
    private volatile int prepareServerTicks;
    private volatile String serverError;

    public ForgeOptimizationQa() {
        if (threads.isThreadCpuTimeSupported() && !threads.isThreadCpuTimeEnabled()) threads.setThreadCpuTimeEnabled(true);
        report.put("synthetic", true);
        report.put("variant", System.getProperty("arcadium.qa.optimization.variant", "unspecified"));
        report.put("samples", samples);
        report.put("unloads", unloads);
    }

    @SubscribeEvent public void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || prepareServerTicks <= 0) return;
        try {
            var server = FMLCommonHandler.instance().getMinecraftServerInstance();
            for (var world : server.field_71305_c) if (world != null) {
                world.func_82736_K().func_82764_b("doMobSpawning", "false");
                world.func_82736_K().func_82764_b("doDaylightCycle", "false");
                world.func_72877_b(6000);
                if (prepareServerTicks == 20 && scene == 0 && world == server.func_71218_a(0)) fixture(world);
                for (Object value : new ArrayList<>(world.field_72996_f))
                    if (value instanceof Entity entity && !(entity instanceof EntityPlayer)) entity.func_70106_y();
            }
            prepareServerTicks--;
        } catch (Throwable error) { serverError = error.toString(); prepareServerTicks = 0; }
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.func_71410_x();
        OneConfigConfig.limitUnfocusedFps = false;
        if (mc.field_71441_e == null || mc.field_71439_g == null) return;
        mc.field_71441_e.func_72877_b(6000);
        mc.field_71439_g.field_70159_w = 0;
        mc.field_71439_g.field_70181_x = 0;
        mc.field_71439_g.field_70179_y = 0;
        if ((stage == 2 || stage == 3) && scene == 2) {
            for (int i = 0; i < 4; i++) {
                int index = lightTick++ % 64;
                int x = -8 + (index % 8) * 2, z = 6 + (index / 8) * 2;
                Block block = ((lightTick / 64) & 1) == 0 ? Block.func_149729_e(89) : Blocks.field_150350_a;
                mc.field_71441_e.func_147465_d(x, 6, z, block, 0, 3);
                if (sampling) lightChanges++;
            }
        }
    }

    @SubscribeEvent public void frame(TickEvent.RenderTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.func_71410_x();
        long now = System.nanoTime();
        frames++;
        try {
            require(now - started < 300_000_000_000L, "Optimization scenario timed out at stage " + stage);
            require(serverError == null, "Server fixture preparation failed: " + serverError);
            if (sampling) {
                if (lastFrame != 0) frameTimes.add(now - lastFrame);
                lastFrame = now;
            }
            switch (stage) {
                case 0 -> {
                    if (frames < 150) return;
                    configure(mc);
                    openWorld(mc, now);
                    stage = 1;
                }
                case 1 -> {
                    if (!ready(mc)) { require(now < deadline, "The benchmark world did not open"); return; }
                    camera(mc);
                    createHud();
                    report.put("worldLoadMillis", (now - after) / 1_000_000d);
                    beginScene(mc, now);
                }
                case 2 -> {
                    if (now < after) return;
                    frameTimes.clear(); lastFrame = 0; sampling = true; sampleStarted = now;
                    sampleWallStarted = System.currentTimeMillis();
                    cpuStarted = cpuTime(); gcStarted = gcTime(); gcCountStarted = gcCount();
                    stage = 3; after = now + DURATION;
                }
                case 3 -> {
                    if (now < after) return;
                    sampling = false;
                    endScene(mc, now);
                    if (++scene < SCENES.length) beginScene(mc, now);
                    else { validate(mc); stage = 4; after = now + 500_000_000L; }
                }
                case 4 -> {
                    if (now < after) return;
                    ForgeRuntimeQa.capture(mc, "optimization-first-person", frames);
                    mc.field_71474_y.field_74320_O = 1;
                    stage = 5; after = now + 500_000_000L;
                }
                case 5 -> {
                    if (now < after) return;
                    ForgeRuntimeQa.capture(mc, "optimization-third-person-back", frames);
                    mc.field_71474_y.field_74320_O = 2;
                    stage = 6; after = now + 500_000_000L;
                }
                case 6 -> {
                    if (now < after) return;
                    ForgeRuntimeQa.capture(mc, "optimization-third-person-front", frames);
                    mc.field_71474_y.field_74320_O = 0;
                    mc.func_147108_a(new GuiInventory(mc.field_71439_g));
                    stage = 7; after = now + 500_000_000L;
                }
                case 7 -> {
                    if (now < after) return;
                    ForgeRuntimeQa.capture(mc, "optimization-inventory", frames);
                    mc.func_147108_a(null);
                    unload(mc, now);
                }
                case 8 -> {
                    if (now < after) return;
                    System.gc();
                    stage = 9; after = now + 1_000_000_000L;
                }
                case 9 -> {
                    if (now < after) return;
                    long alive = worlds.stream().filter(ref -> ref.get() != null).count();
                    unloads.add(Map.of("cycle", reloads, "previousWorldsAlive", alive,
                        "previousConnectionsAlive", connections.stream().filter(ref -> ref.get() != null).count(),
                        "previousServersAlive", servers.stream().filter(ref -> ref.get() != null).count(),
                        "heapUsedBytes", heap(), "gcMillis", gcTime()));
                    write(false, null);
                    if (++reloads == 3) { report.put("worldReferencesAliveAfterFinalUnload", alive); finish(mc); return; }
                    openWorld(mc, now); stage = 10;
                }
                case 10 -> {
                    if (!ready(mc)) { require(now < deadline, "A reload did not open the benchmark world"); return; }
                    camera(mc); stage = 11; after = now + 3_000_000_000L;
                }
                case 11 -> {
                    if (now < after) return;
                    unload(mc, now);
                }
                case 12 -> {
                    require(now < deadline, "The integrated server did not prepare the benchmark scene");
                    if (prepareServerTicks > 0) return;
                    for (Object value : new ArrayList<>(mc.field_71441_e.field_72996_f)) if (value instanceof Entity entity &&
                            !(entity instanceof EntityPlayer) && (entity.func_145782_y() < 900000 || entity.func_145782_y() >= 900000 + ITEMS))
                        mc.field_71441_e.func_72900_e(entity);
                    if (scene == 1) spawnItems(mc);
                    stage = 2; after = now + WARMUP;
                }
            }
        } catch (Throwable error) {
            error.printStackTrace();
            try { write(false, error.toString()); } catch (Exception suppressed) { error.addSuppressed(suppressed); }
            done = true; mc.func_71400_g();
        }
    }

    private void configure(Minecraft mc) {
        var expectedMods = new ArrayList<String>();
        for (String mod : System.getProperty("arcadium.qa.optimization.expectedMods", "arcadiumclient,angelica,gtnhlib,lwjgl3ify").split(",")) {
            if (!mod.isBlank()) { require(Loader.isModLoaded(mod), "A mandatory native Forge mod did not load: " + mod); expectedMods.add(mod); }
        }
        report.put("expectedNativeMods", expectedMods);
        if (Loader.isModLoaded("hodgepodge") || Loader.isModLoaded("archaicfix")) {
            require(Loader.isModLoaded("unimixins"), "Optimization mods require the aggregate UniMixins mod identity");
            report.put("unimixinsVersion", Loader.instance().getIndexedModList().get("unimixins").getVersion());
        }
        var engines = new LinkedHashMap<String, Object>();
        for (String name : List.of("org.spongepowered.asm.launch.MixinBootstrap", "com.llamalad7.mixinextras.MixinExtrasBootstrap")) {
            try {
                Class<?> type = Class.forName(name);
                var engine = new LinkedHashMap<String, Object>();
                var origin = type.getProtectionDomain().getCodeSource();
                engine.put("source", origin == null ? null : origin.getLocation().toString());
                try { engine.put("version", type.getField("VERSION").get(null)); }
                catch (NoSuchFieldException ignored) {
                    try { engine.put("version", type.getMethod("getVersion").invoke(null)); }
                    catch (NoSuchMethodException missing) { engine.put("version", type.getPackage().getImplementationVersion()); }
                }
                engines.put(name, engine);
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Could not inspect the active Mixin engine", error); }
        }
        report.put("mixinEngines", engines);
        mc.field_71474_y.field_74320_O = 0;
        mc.field_71474_y.field_74350_i = 1000;
        mc.field_71474_y.field_151451_c = 8;
        mc.field_71474_y.field_74333_Y = .7f;
        mc.field_71474_y.field_74335_Z = 2;
        mc.field_71474_y.field_82881_y = false;
        mc.field_71474_y.field_74352_v = false;
        mc.field_71474_y.field_74347_j = true;
        OneConfigConfig.limitUnfocusedFps = false;
        NoRenderConfig.setEnabled(false); NoRender.refresh();
        Display.setVSyncEnabled(false);
        mc.field_71474_y.func_74303_b();
        report.put("renderDistance", 8); report.put("fpsLimitSetting", 1000); report.put("vsync", false);
        report.put("warmupSeconds", WARMUP / 1_000_000_000L); report.put("sampleSeconds", DURATION / 1_000_000_000L);
        report.put("camera", List.of(.5, 9.0, .5, 0.0, 15.0)); report.put("fixtureItems", ITEMS);
        report.put("renderer", GL11.glGetString(GL11.GL_RENDERER)); report.put("glVersion", GL11.glGetString(GL11.GL_VERSION));
        report.put("width", mc.field_71443_c); report.put("height", mc.field_71440_d);
        report.put("nativeMods", Loader.instance().getActiveModList().stream().map(mod -> mod.getModId() + ":" + mod.getVersion()).toList());
        var configs = new LinkedHashMap<String, Object>();
        for (String name : List.of("com.mitchej123.hodgepodge.config.SpeedupsConfig", "com.mitchej123.hodgepodge.config.TweaksConfig",
                "org.embeddedt.archaicfix.config.ArchaicConfig", "makamys.coretweaks.Config")) {
            try {
                var fields = new LinkedHashMap<String, Object>();
                for (var field : Class.forName(name).getFields()) if (Modifier.isStatic(field.getModifiers()) &&
                        (field.getType().isPrimitive() || field.getType().isEnum() || field.getType() == String.class)) {
                    Object value = field.get(null);
                    fields.put(field.getName(), value instanceof Enum<?> item ? item.name() : value);
                }
                configs.put(name, fields);
            } catch (ClassNotFoundException ignored) {
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Could not read optimization configuration", error); }
        }
        report.put("optimizationConfigs", configs);
    }

    private void openWorld(Minecraft mc, long now) {
        after = now; deadline = now + 90_000_000_000L;
        mc.func_71371_a("arcadium-optimization-qa", "Arcadium Optimization QA", new WorldSettings(71,
            WorldSettings.func_77161_a(1), false, false, WorldType.field_77138_c).func_77166_b());
    }

    private static boolean ready(Minecraft mc) { return mc.field_71441_e != null && mc.field_71439_g != null && mc.field_71462_r == null; }

    private static void camera(Minecraft mc) {
        mc.field_71439_g.field_71075_bZ.field_75100_b = true;
        mc.field_71439_g.func_70080_a(.5, 9, .5, 0, 15);
        mc.field_71439_g.field_71071_by.field_70462_a[0] = new ItemStack(Blocks.field_150348_b);
        mc.field_71439_g.field_71071_by.field_70461_c = 0;
    }

    private static void fixture(net.minecraft.world.World world) {
        for (int x = -12; x <= 12; x++) for (int z = 4; z <= 28; z++)
            world.func_147465_d(x, 4, z, Blocks.field_150348_b, 0, 2);
        for (int x = -9; x <= 9; x += 6) for (int z = 10; z <= 28; z += 6) for (int y = 5; y <= 12; y++)
            world.func_147465_d(x, y, z, Blocks.field_150348_b, 0, 2);
    }

    private void beginScene(Minecraft mc, long now) throws Exception {
        for (int index = 0; index < ITEMS; index++) mc.field_71441_e.func_73028_b(900000 + index);
        report.put("phase", SCENES[scene]); stage = 12; prepareServerTicks = 20; deadline = now + 10_000_000_000L;
        write(false, null);
    }

    private static void spawnItems(Minecraft mc) {
        for (int index = 0; index < ITEMS; index++) {
            var item = new EntityItem(mc.field_71441_e, -7.5 + index % 16, 7, 9 + index / 16,
                new ItemStack(Blocks.field_150348_b));
            item.field_70159_w = 0; item.field_70181_x = 0; item.field_70179_y = 0;
            mc.field_71441_e.func_73027_a(900000 + index, item);
        }
    }

    private void endScene(Minecraft mc, long now) throws Exception {
        require(frameTimes.size() >= 100, "Not enough frame samples");
        var sorted = frameTimes.stream().sorted().toList();
        double mean = frameTimes.stream().mapToLong(Long::longValue).average().orElseThrow() / 1_000_000d;
        int lowCount = Math.max(1, sorted.size() / 100);
        double lowMean = sorted.subList(sorted.size() - lowCount, sorted.size()).stream().mapToLong(Long::longValue).average().orElseThrow() / 1_000_000d;
        var sample = new LinkedHashMap<String, Object>();
        sample.put("scene", SCENES[scene]); sample.put("frames", frameTimes.size()); sample.put("meanFrameMs", mean);
        sample.put("averageFps", 1000 / mean); sample.put("onePercentLowFps", 1000 / lowMean);
        sample.put("frameMsP50", sorted.get(sorted.size() / 2) / 1_000_000d);
        sample.put("frameMsP95", sorted.get((int) ((sorted.size() - 1) * .95)) / 1_000_000d);
        sample.put("frameMsP99", sorted.get((int) ((sorted.size() - 1) * .99)) / 1_000_000d);
        sample.put("cpuMsPerFrame", (cpuTime() - cpuStarted) / 1_000_000d / frameTimes.size());
        sample.put("gcMillis", gcTime() - gcStarted); sample.put("gcCount", gcCount() - gcCountStarted);
        sample.put("heapUsedBytes", heap()); sample.put("lightChanges", lightChanges);
        sample.put("sampleNanos", now - sampleStarted); sample.put("focused", Display.isActive());
        sample.put("startedMillis", sampleWallStarted); sample.put("endedMillis", System.currentTimeMillis());
        int activeItems = 0;
        for (int index = 0; index < ITEMS; index++) if (mc.field_71441_e.func_73045_a(900000 + index) instanceof EntityItem) activeItems++;
        sample.put("activeFixtureItems", activeItems);
        sample.put("worldEntityCount", mc.field_71441_e.field_72996_f.size());
        sample.put("camera", List.of(mc.field_71439_g.field_70165_t, mc.field_71439_g.field_70163_u,
            mc.field_71439_g.field_70161_v, mc.field_71439_g.field_70177_z, mc.field_71439_g.field_70125_A));
        int geometry = 0;
        for (int x = -12; x <= 12; x++) for (int z = 4; z <= 28; z++)
            if (mc.field_71441_e.func_147439_a(x, 4, z) == Blocks.field_150348_b) geometry++;
        for (int x = -9; x <= 9; x += 6) for (int z = 10; z <= 28; z += 6) for (int y = 5; y <= 12; y++)
            if (mc.field_71441_e.func_147439_a(x, y, z) == Blocks.field_150348_b) geometry++;
        sample.put("geometryBlocks", geometry);
        samples.add(sample);
        Files.createDirectories(output.getParent());
        try (var data = new DataOutputStream(Files.newOutputStream(output.resolveSibling(output.getFileName() + "." + SCENES[scene] + ".frames")))) {
            for (long time : frameTimes) data.writeLong(time);
        }
        write(false, null);
    }

    private void validate(Minecraft mc) {
        require(fpsHud.getRenderedW() > 0 && fpsHud.getRenderedH() > 0, "FPS HUD did not render");
        mc.field_71441_e.func_147465_d(0, 5, 3, Block.func_149729_e(31), 1, 2);
        NoRenderConfig.setEnabled(true); NoRenderConfig.setGrass(true); NoRender.refresh();
        var grass = mc.field_71441_e.func_147439_a(0, 5, 3);
        require(Block.func_149682_b(grass) == 31 && NoRender.hideBlock(grass, 0, 5, 3, mc.field_71441_e), "NoRender changed world data or did not hide grass");
        NoRenderConfig.setEnabled(false); NoRenderConfig.setGrass(false); NoRender.refresh();
        require(!NoRender.hideBlock(grass, 0, 5, 3, mc.field_71441_e), "NoRender did not restore grass rendering");
        report.put("hudRendered", true); report.put("noRenderPreservesBlockData", true);
    }

    private void unload(Minecraft mc, long now) throws Exception {
        worlds.add(new WeakReference<>(mc.field_71441_e));
        connections.add(new WeakReference<>(mc.func_147114_u()));
        servers.add(new WeakReference<>(mc.func_71401_C()));
        mc.field_71441_e.func_72882_A();
        mc.func_71403_a(null);
        mc.func_147108_a(new GuiMainMenu());
        stage = 8; after = now + 2_000_000_000L;
        require(mc.field_71441_e == null, "The old world remained active after disconnect");
        write(false, null);
    }

    private void finish(Minecraft mc) throws Exception {
        if (Loader.isModLoaded("NotEnoughItems")) {
            int items = ((List<?>) Class.forName("codechicken.nei.ItemList").getField("items").get(null)).size();
            require(items > 0, "NEI did not populate its native item list");
            report.put("neiItemCount", items);
        }
        report.put("phase", "complete"); report.put("worldReloads", reloads - 1);
        write(true, null); done = true; mc.func_71400_g();
    }

    private void write(boolean passed, String error) throws Exception {
        report.put("passed", passed); report.put("error", error); report.put("stage", stage);
        report.put("elapsedMillis", (System.nanoTime() - started) / 1_000_000d);
        report.put("processId", ProcessHandle.current().pid());
        Files.createDirectories(output.getParent());
        Files.writeString(output, new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(report));
    }

    private void createHud() {
        HudManager manager = HudManager.INSTANCE;
        var provider = manager.getProvider(FpsHud.class);
        require(provider != null, "FPS HUD provider is missing");
        fpsHud = manager.getActiveInstances().stream().filter(FpsHud.class::isInstance).findFirst().orElse(null);
        if (fpsHud == null) {
            fpsHud = provider.make(null); HudManager.markProviderKnown(fpsHud); manager.getActiveInstances().add(fpsHud);
            manager.getInstancesRevision().setIntValue(manager.getInstancesRevision().getIntValue() + 1); fpsHud.setup();
        }
        fpsHud.setSection(Section.TopLeft); fpsHud.setRelativeX(16f); fpsHud.setRelativeY(16f);
        fpsHud.setHidden(false); fpsHud.setShowBackground(true); HudManager.invalidate();
    }

    private long cpuTime() { return threads.isCurrentThreadCpuTimeSupported() ? threads.getCurrentThreadCpuTime() : 0; }
    private static long heap() { return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(); }
    private static long gcTime() { return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean -> Math.max(0, bean.getCollectionTime())).sum(); }
    private static long gcCount() { return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean -> Math.max(0, bean.getCollectionCount())).sum(); }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
