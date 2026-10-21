// SPDX-License-Identifier: MIT
package io.github.arcadiumclient.forge1710.test;

import com.google.gson.GsonBuilder;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import io.github.arcadiumclient.runtime.client.features.GuiScaleConfig;
import io.github.arcadiumclient.runtime.client.features.NoRender;
import io.github.arcadiumclient.runtime.client.features.NoRenderConfig;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import org.lwjglx.opengl.Display;
import org.polyfrost.evergreenhud.client.hud.FpsHud;
import org.polyfrost.oneconfig.api.hud.v1.Hud;
import org.polyfrost.oneconfig.api.hud.v1.HudManager;
import org.polyfrost.oneconfig.api.hud.v1.Section;

public final class ForgeWorldFlowQa {
    private int frames, stage, next = 180;
    private long started, worldStarted;
    private long sampleStart, sampleEnd;
    private long actionAfter;
    private Hud fpsHud;
    private boolean done;
    private final java.util.List<Double> frameTimes = new java.util.ArrayList<>();

    @SubscribeEvent public void frame(TickEvent.RenderTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.func_71410_x();
        long now = System.nanoTime();
        if (started != 0 && mc.field_71441_e != null && stage == 2 && now >= sampleStart) frameTimes.add((now - started) / 1_000_000d);
        started = now;
        if (++frames < next) return;
        try {
            switch (stage) {
                case 0 -> {
                    stage = 1; next = frames + 100; worldStarted = now;
                    mc.field_71474_y.field_74320_O = 0;
                    mc.field_71474_y.field_74350_i = 260;
                    mc.field_71474_y.field_151451_c = 8;
                    mc.field_71474_y.field_74333_Y = .7f;
                    mc.field_71474_y.field_74335_Z = 2;
                    mc.field_71474_y.field_82881_y = false;
                    mc.field_71474_y.field_74352_v = false;
                    Display.setVSyncEnabled(false);
                    mc.field_71474_y.func_74303_b();
                    mc.func_71371_a("arcadium-forge-qa", "Arcadium Forge QA", new WorldSettings(71,
                        WorldSettings.func_77161_a(1), false, false, WorldType.field_77138_c).func_77166_b());
                    return;
                }
                case 1 -> {
                    if (mc.field_71441_e == null || mc.field_71439_g == null || mc.field_71462_r != null) {
                        if (now - worldStarted > 120_000_000_000L) throw new IllegalStateException("The Forge test world did not open.");
                        return;
                    }
                    mc.field_71439_g.func_70080_a(.5, 6, .5, 0, 15);
                    mc.field_71439_g.field_71071_by.field_70462_a[0] = new ItemStack(Blocks.field_150348_b);
                    mc.field_71439_g.field_71071_by.field_70461_c = 0;
                    createHud();
                    sampleStart = now + 10_000_000_000L;
                    sampleEnd = sampleStart + 30_000_000_000L;
                    stage = 2; next = frames + 600;
                    return;
                }
                case 2 -> {
                    if (now < sampleEnd) return;
                    if (fpsHud.getRenderedW() <= 0 || fpsHud.getRenderedH() <= 0) throw new IllegalStateException("The FPS HUD did not render through Forge.");
                    ForgeRuntimeQa.capture(mc, "forge-world-first-person", frames); mc.field_71474_y.field_74320_O = 1;
                }
                case 3 -> { ForgeRuntimeQa.capture(mc, "forge-world-third-person-back", frames); mc.field_71474_y.field_74320_O = 2; }
                case 4 -> { ForgeRuntimeQa.capture(mc, "forge-world-third-person-front", frames); mc.field_71474_y.field_74320_O = 0; }
                case 5 -> {
                    GuiScaleConfig.setEnabled(true); GuiScaleConfig.setHotbarScale(0); GuiScaleConfig.setCrosshairScale(0);
                }
                case 6 -> {
                    ForgeRuntimeQa.capture(mc, "forge-world-small-hud", frames);
                    GuiScaleConfig.setEnabled(false);
                    mc.func_147108_a(new net.minecraft.client.gui.inventory.GuiInventory(mc.field_71439_g));
                }
                case 7 -> {
                    ForgeRuntimeQa.capture(mc, "forge-world-inventory", frames);
                    mc.func_147108_a(null);
                    mc.field_71441_e.func_147465_d(0, 4, 3, net.minecraft.block.Block.func_149729_e(31), 1, 2);
                    NoRenderConfig.setEnabled(false); NoRender.refresh();
                    actionAfter = now + 2_000_000_000L;
                }
                case 8 -> {
                    if (now < actionAfter) return;
                    ForgeRuntimeQa.capture(mc, "forge-grass-visible", frames);
                    NoRenderConfig.setEnabled(true); NoRenderConfig.setGrass(true); NoRender.refresh();
                    mc.field_71438_f.func_72712_a();
                    actionAfter = now + 2_000_000_000L;
                }
                case 9 -> {
                    if (now < actionAfter) return;
                    var block = mc.field_71441_e.func_147439_a(0, 4, 3);
                    if (!NoRender.hideBlock(block, 0, 4, 3, mc.field_71441_e) || net.minecraft.block.Block.func_149682_b(block) != 31)
                        throw new IllegalStateException("NoRender changed block data or did not activate.");
                    ForgeRuntimeQa.capture(mc, "forge-grass-hidden", frames);
                    NoRenderConfig.setEnabled(false); NoRenderConfig.setGrass(false); NoRender.refresh();
                    report(mc, true, null); done = true; mc.func_71400_g(); return;
                }
            }
            stage++; next = frames + 90;
        } catch (Throwable error) {
            error.printStackTrace();
            try { report(mc, false, error.toString()); } catch (Exception writeFailure) { error.addSuppressed(writeFailure); }
            done = true; mc.func_71400_g();
        }
    }

    private void report(Minecraft mc, boolean ok, String error) throws Exception {
        var result = new LinkedHashMap<String, Object>();
        result.put("ok", ok); result.put("error", error); result.put("stage", stage);
        result.put("processId", ProcessHandle.current().pid()); result.put("capturedAt", java.time.Instant.now().toString());
        result.put("worldLoaded", mc.field_71441_e != null); result.put("samples", frameTimes.size());
        result.put("userGameDir", mc.field_71412_D.getAbsolutePath()); result.put("runtimeDir", java.nio.file.Path.of("").toAbsolutePath().toString());
        if (cpw.mods.fml.common.Loader.isModLoaded("NotEnoughItems")) {
            var type = Class.forName("codechicken.nei.ItemList");
            int items = ((java.util.List<?>) type.getField("items").get(null)).size();
            if (items == 0) throw new IllegalStateException("NEI did not populate its native item list.");
            result.put("neiItemCount", items);
        }
        if (fpsHud != null) {
            var text = FpsHud.class.getDeclaredMethod("getText"); text.setAccessible(true);
            result.put("fpsHud", java.util.Map.of("width", fpsHud.getRenderedW(), "height", fpsHud.getRenderedH(), "text", text.invoke(fpsHud)));
        }
        if (!frameTimes.isEmpty()) {
            var sorted = frameTimes.stream().sorted().toList();
            result.put("averageFps", 1000 / frameTimes.stream().mapToDouble(Double::doubleValue).average().orElseThrow());
            result.put("frameMsP50", sorted.get(sorted.size() / 2));
            result.put("frameMsP95", sorted.get((int) (sorted.size() * .95)));
        }
        result.put("renderDistance", mc.field_71474_y.field_151451_c); result.put("vsync", mc.field_71474_y.field_74352_v);
        Files.writeString(mc.field_71412_D.toPath().resolve("forge-world-flow.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result));
    }

    private void createHud() {
        HudManager manager = HudManager.INSTANCE;
        var provider = manager.getProvider(FpsHud.class);
        if (provider == null) throw new IllegalStateException("The FPS provider is missing from Forge.");
        fpsHud = manager.getActiveInstances().stream().filter(FpsHud.class::isInstance).findFirst().orElse(null);
        if (fpsHud == null) {
            fpsHud = provider.make(null);
            HudManager.markProviderKnown(fpsHud);
            manager.getActiveInstances().add(fpsHud);
            manager.getInstancesRevision().setIntValue(manager.getInstancesRevision().getIntValue() + 1);
            fpsHud.setup();
        }
        fpsHud.setSection(Section.TopLeft);
        fpsHud.setRelativeX(16f); fpsHud.setRelativeY(16f);
        fpsHud.setHidden(false); fpsHud.setShowBackground(true);
        HudManager.invalidate();
    }
}
