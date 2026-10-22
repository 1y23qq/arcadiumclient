// SPDX-License-Identifier: MIT
package io.github.arcadiumclient.forge1710.test;

import com.google.gson.GsonBuilder;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.scoreboard.IScoreObjectiveCriteria;
import org.lwjglx.input.Keyboard;
import org.lwjglx.opengl.Display;
import org.lwjgl.sdl.SDLVideo;
import org.polyfrost.oneconfig.api.event.v1.EventManager;
import org.polyfrost.oneconfig.api.event.v1.events.KeyInputEvent;
import org.polyfrost.oneconfig.internal.legacy.KeyCodes;
import org.polyfrost.polyzoom.PolyZoom;
import org.polyfrost.evergreenhud.client.hud.PingHud;
import org.polyfrost.oneconfig.api.hud.v1.HudManager;
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeybindManager;
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeyModifiers;
import org.polyfrost.oneconfig.api.ui.v1.keybind.OneConfigKeybind;
import org.codeberg.chromatic.freelook.handler.FreelookHandler;

@me.eigenraven.lwjgl3ify.api.Lwjgl3Aware
public final class ForgeControlsQa {
    private final LinkedHashMap<String, Object> report = new LinkedHashMap<>();
    private final ArrayList<String> events = new ArrayList<>();
    private int stage, frames;
    private long after;
    private boolean done;
    private PingHud ping;
    private OneConfigKeybind mouseBind;
    private long mousePressedAt;

    public ForgeControlsQa() {
        EventManager.register(KeyInputEvent.class, event -> events.add(event.toString()));
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) throws Exception {
        if (done || stage != 8 || event.phase != TickEvent.Phase.END || System.nanoTime() < after) return;
        report.put("fullscreenAfterMinimize", Display.isFullscreen());
        if (!Display.isActive()) {
            org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps = true;
            org.polyfrost.oneconfig.internal.OneConfigConfig.unfocusedFps = 45;
            report.put("unfocusedLimitEnabled", com.gtnewhorizons.angelica.rendering.FpsReducer.effectiveCap(260, false));
            org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps = false;
            report.put("unfocusedLimitDisabled", com.gtnewhorizons.angelica.rendering.FpsReducer.effectiveCap(260, false));
            org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps = true;
        }
        stage = 9; after = System.nanoTime() + 400_000_000L;
    }

    @SubscribeEvent public void frame(TickEvent.RenderTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || ++frames < 150 || System.nanoTime() < after) return;
        Minecraft mc = Minecraft.func_71410_x();
        try {
            switch (stage) {
                case 0 -> {
                    report.put("keyboardHooks", java.util.Arrays.stream(Keyboard.class.getDeclaredMethods())
                        .map(java.lang.reflect.Method::getName).filter(name -> name.contains("Callback") || name.contains("oneconfig")).toList());
                    report.put("mappedC", KeyCodes.fromLegacy(Keyboard.KEY_C).getValue());
                    report.put("mappedRightShift", KeyCodes.fromLegacy(Keyboard.KEY_RSHIFT).getValue());
                    mc.field_71474_y.field_74350_i = 180;
                    mc.field_71474_y.field_82881_y = false;
                    mc.field_71474_y.field_74335_Z = 2;
                    mc.func_71371_a("arcadium-controls-qa", "Arcadium Controls QA", new WorldSettings(71,
                        WorldSettings.func_77161_a(1), false, false, WorldType.field_77138_c).func_77166_b());
                }
                case 1 -> {
                    if (mc.field_71441_e == null || mc.field_71439_g == null || mc.field_71462_r != null) return;
                    mc.field_71439_g.func_70080_a(.5, 6, .5, 0, 15);
                    var scoreboard = mc.field_71441_e.func_96441_U();
                    var objective = scoreboard.func_96518_b("controls_qa");
                    if (objective == null) objective = scoreboard.func_96535_a("controls_qa", IScoreObjectiveCriteria.field_96641_b);
                    objective.func_96681_a("\u00a7eScoreboard QA");
                    scoreboard.func_96529_a("\u00a7fWhite scoreboard text", objective).func_96647_c(3);
                    scoreboard.func_96529_a("\u00a7aGreen scoreboard text", objective).func_96647_c(2);
                    scoreboard.func_96529_a("\u00a7bCyan scoreboard text", objective).func_96647_c(1);
                    scoreboard.func_96530_a(1, objective);
                    key(Keyboard.KEY_C, true);
                }
                case 2 -> {
                    var zooming = PolyZoom.class.getDeclaredField("zooming"); zooming.setAccessible(true);
                    report.put("zoomPressed", zooming.get(PolyZoom.instance()));
                    ForgeRuntimeQa.capture(mc, "forge-controls-zoom", frames);
                    key(Keyboard.KEY_C, false);
                }
                case 3 -> {
                    var zooming = PolyZoom.class.getDeclaredField("zooming"); zooming.setAccessible(true);
                    report.put("zoomReleased", zooming.get(PolyZoom.instance()));
                    key(Keyboard.KEY_RSHIFT, true);
                }
                case 4 -> {
                    report.put("rightShiftScreen", mc.field_71462_r == null ? null : mc.field_71462_r.getClass().getName());
                    key(Keyboard.KEY_RSHIFT, false);
                    ForgeRuntimeQa.capture(mc, "forge-controls-options", frames);
                    mc.func_147108_a(null);
                    mc.func_71351_a(new ServerData("Status QA", System.getProperty("arcadium.qa.statusHost", "127.0.0.1:25575")));
                }
                case 5 -> {
                    mouseBind = new OneConfigKeybind(null, new int[]{4}, KeyModifiers.NONE, 0L, down -> {
                        FreelookHandler.INSTANCE.setKeyDown(down);
                        report.put(down ? "mouseFreelookPressed" : "mouseFreelookReleased", FreelookHandler.INSTANCE.freelookToggled);
                        if (down) report.put("mouseActivationMs", (System.nanoTime() - mousePressedAt) / 1_000_000d);
                        return true;
                    });
                    KeybindManager.register(mouseBind);
                    mousePressedAt = System.nanoTime(); org.lwjglx.input.Mouse.addButtonEvent(3, true);
                    ping = (PingHud) HudManager.INSTANCE.getProvider(PingHud.class).make(null);
                    ping.setup();
                    var statusAddress = PingHud.class.getDeclaredField("statusAddress"); statusAddress.setAccessible(true);
                    statusAddress.set(ping, mc.func_147104_D().field_78845_b);
                    var request = PingHud.class.getDeclaredMethod("requestPing", String.class); request.setAccessible(true);
                    request.invoke(ping, mc.func_147104_D().field_78845_b);
                    stage++; after = System.nanoTime() + 2_000_000_000L; return;
                }
                case 6 -> {
                    org.lwjglx.input.Mouse.addButtonEvent(3, false);
                    var value = PingHud.class.getDeclaredField("ping"); value.setAccessible(true);
                    report.put("measuredPing", value.get(ping));
                    var playerInfo = new net.minecraft.client.gui.GuiPlayerInfo(mc.field_71439_g.func_70005_c_());
                    mc.func_147114_u().field_147303_b.clear();
                    mc.func_147114_u().field_147303_b.add(playerInfo);
                    var fallback = PingHud.class.getDeclaredMethod("serverReportedPing"); fallback.setAccessible(true);
                    playerInfo.field_78829_b = 73;
                    report.put("serverReportedPing", fallback.invoke(ping));
                    playerInfo.field_78829_b = 0;
                    report.put("zeroPing", fallback.invoke(ping));
                    var height = new io.github.arcadiumclient.runtime.client.features.HeightLimitHud();
                    height.setStyle(1);
                    var heightText = height.getClass().getDeclaredMethod("getText"); heightText.setAccessible(true);
                    height.setSpaceBeforeDistance(false);
                    String compact = (String) heightText.invoke(height);
                    height.setSpaceBeforeDistance(true);
                    String spaced = (String) heightText.invoke(height);
                    if (!spaced.equals(compact.replace("(", " ("))) throw new IllegalStateException("Height Limit spacing did not update.");
                    report.put("heightCompact", compact); report.put("heightSpaced", spaced);
                    var focused = Display.class.getDeclaredField("displayFocused"); focused.setAccessible(true);
                    boolean priorFocus = focused.getBoolean(null);
                    boolean priorEnabled = org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps;
                    int priorCap = org.polyfrost.oneconfig.internal.OneConfigConfig.unfocusedFps;
                    try {
                        focused.setBoolean(null, false);
                        org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps = true;
                        org.polyfrost.oneconfig.internal.OneConfigConfig.unfocusedFps = 45;
                        int enabled = com.gtnewhorizons.angelica.rendering.FpsReducer.effectiveCap(260, false);
                        org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps = false;
                        int disabled = com.gtnewhorizons.angelica.rendering.FpsReducer.effectiveCap(260, false);
                        if (enabled != 45 || disabled != 260) throw new IllegalStateException("The unfocused FPS toggle did not reach Angelica.");
                        report.put("unfocusedLimitEnabled", enabled); report.put("unfocusedLimitDisabled", disabled);
                    } finally {
                        focused.setBoolean(null, priorFocus);
                        org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps = priorEnabled;
                        org.polyfrost.oneconfig.internal.OneConfigConfig.unfocusedFps = priorCap;
                    }
                    ForgeRuntimeQa.capture(mc, "forge-controls-scoreboard", frames);
                    mc.func_71351_a(null);
                    if (!Display.isFullscreen()) mc.func_71352_k();
                }
                case 7 -> {
                    report.put("fullscreenEntered", Display.isFullscreen());
                    me.eigenraven.lwjgl3ify.client.MainThreadExec.runOnMainThread(() -> {
                        SDLVideo.SDL_MinimizeWindow(Display.getWindow());
                        Thread.ofPlatform().daemon().start(() -> {
                            try { Thread.sleep(700); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                            me.eigenraven.lwjgl3ify.client.MainThreadExec.runOnMainThread(() -> SDLVideo.SDL_RestoreWindow(Display.getWindow()));
                        });
                    });
                }
                case 8 -> {
                    report.put("fullscreenAfterMinimize", Display.isFullscreen());
                }
                case 9 -> {
                    report.put("fullscreenAfterRestore", Display.isFullscreen());
                    if (Display.isFullscreen()) mc.func_71352_k();
                }
                case 10 -> {
                    KeybindManager.unregister(mouseBind);
                    report.put("manualWindowed", !Display.isFullscreen());
                    report.put("events", events);
                    if (!Boolean.TRUE.equals(report.get("zoomPressed")) || !Boolean.FALSE.equals(report.get("zoomReleased")) ||
                        report.get("rightShiftScreen") == null || !Boolean.TRUE.equals(report.get("mouseFreelookPressed")) ||
                        !Boolean.FALSE.equals(report.get("mouseFreelookReleased")) || !Boolean.TRUE.equals(report.get("fullscreenAfterRestore")) ||
                        !Boolean.TRUE.equals(report.get("manualWindowed")) || !Long.valueOf(73).equals(report.get("serverReportedPing")) ||
                        !Long.valueOf(0).equals(report.get("zeroPing"))) throw new IllegalStateException("A controls regression check failed.");
                    report.put("ok", true);
                    report.put("finished", true);
                    write(mc); done = true; mc.func_71400_g(); return;
                }
            }
            stage++; after = System.nanoTime() + 400_000_000L; write(mc);
        } catch (Throwable error) {
            error.printStackTrace(); report.put("ok", false); report.put("error", error.toString());
            report.put("finished", true);
            try { write(mc); } catch (Exception ignored) {}
            done = true; mc.func_71400_g();
        }
    }

    private static void key(int code, boolean down) {
        Keyboard.addRawKeyEvent(new Keyboard.KeyEvent(code, code, 0,
            down ? Keyboard.KeyState.PRESS : Keyboard.KeyState.RELEASE, System.nanoTime()));
    }

    private void write(Minecraft mc) throws Exception {
        report.put("stage", stage);
        Files.writeString(mc.field_71412_D.toPath().resolve("forge-controls.json"), new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(report));
    }
}
