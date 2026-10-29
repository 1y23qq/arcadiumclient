package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import io.github.arcadiumclient.runtime.client.ClientPreferences;
import io.github.arcadiumclient.runtime.client.features.*;
import io.github.arcadiumclient.runtime.client.gui.ArcadiumServersScreen;
import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.menu.TitleScreen;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.client.options.ServerListEntry;
import org.polyfrost.oneconfig.internal.OneConfigConfig;

public final class Feature058Qa {
    public static boolean recording;
    public static final List<Integer> colors = new ArrayList<>();
    private static final Map<String, Object> result = new LinkedHashMap<>();
    private static int ticks, frames, stage;
    private static boolean done;
    public static boolean enabled() { return System.getProperty("arcadium.release058.output") != null; }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }

    public static void tick() {
        if (done) return;
        var mc = Minecraft.getInstance();
        try {
            require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()), "Use an isolated offline account");
            require(++ticks < 1200, "Feature QA timed out at stage " + stage);
            OneConfigConfig.limitUnfocusedFps = false;
            mc.options.pauseOnUnfocus = false;
            if (stage >= 2) net.minecraft.client.options.KeyBinding.set(mc.options.playerListKey.getKeyCode(), true);
            if (stage == 0 && ticks > 60) {
                ClientPreferences.setSetupComplete(true);
                var method = ArcadiumServersScreen.class.getDeclaredMethod("connect", ServerListEntry.class);
                method.setAccessible(true);
                var screen = new ArcadiumServersScreen(new TitleScreen());
                mc.openScreen(screen);
                method.invoke(screen, new ServerListEntry("Local QA", System.getProperty("arcadium.release058.server"), false));
                stage = 1;
            } else if (stage == 1 && mc.world != null && mc.player != null && mc.screen == null) {
                var handler = mc.getNetworkHandler();
                for (var field : handler.getClass().getDeclaredFields()) {
                    if (!Map.class.isAssignableFrom(field.getType())) continue;
                    field.setAccessible(true);
                    var map = (Map<?, ?>) field.get(handler);
                    if (map == null || map.isEmpty() || !(map.values().iterator().next() instanceof PlayerInfo)) continue;
                    @SuppressWarnings("unchecked") var players = (Map<UUID, PlayerInfo>) map;
                    var id = UUID.fromString("00000000-0000-0000-0000-000000000058");
                    players.put(id, new PlayerInfo(new GameProfile(id, "OtherPlayer")));
                }
                require(handler.getOnlinePlayers().size() == 2, "Missing second tab player");
                mc.setCurrentServerEntry(new ServerListEntry("CompMC fixture", "COMPMC.NET.:25565", false));
                require(HeightLimitSource.snapshot().getSupported() && HeightLimitSource.snapshot().getMaximum() == 100, "CompMC limit was not applied");
                var config = Class.forName("tomeko.hybedwars.config.HyBedWarsConfig").getField("INSTANCE").get(null);
                var toggle = config.getClass().getDeclaredField("compMcHeightLimit");
                toggle.setAccessible(true);
                toggle.setBoolean(config, false);
                require(!HeightLimitSource.snapshot().getSupported(), "CompMC limit toggle did not disable immediately");
                toggle.setBoolean(config, true);
                require(HeightLimitSource.minemenOverlayActive(), "CompMC block overlay is inactive");
                mc.setCurrentServerEntry(new ServerListEntry("Other fixture", "example.test", false));
                require(!HeightLimitSource.snapshot().getSupported(), "CompMC limit leaked to another server");
                mc.setCurrentServerEntry(new ServerListEntry("Minemen fixture", "na.minemen.club", false));
                require(HeightLimitSource.snapshot().getMaximum() == 100, "Minemen limit regressed");
                result.put("serverLimitsAndToggle", true);
                var hud = new ToggleSprintHud();
                var text = ToggleSprintHud.class.getDeclaredMethod("getText");
                text.setAccessible(true);
                ToggleSprintConfig.setEnabled(true);
                ToggleSprintConfig.toggleSprintState = false;
                ToggleSprintConfig.toggleSneakState = false;
                mc.player.abilities.flying = true;
                require(hud.shouldShow() && text.invoke(hud).equals("Flying"), "Flying HUD missing without sprint");
                ToggleSprintConfig.setShowSprintTextWhileFlying(false);
                require(!hud.shouldShow(), "Flying HUD did not respect its switch");
                ToggleSprintConfig.setShowSprintTextWhileFlying(true);
                mc.player.abilities.flying = false;
                ToggleSprintConfig.toggleSprintState = true;
                mc.player.onGround = false;
                require(!text.invoke(hud).equals("Flying"), "Airborne non-flying player displayed Flying");
                require(text.invoke(hud).equals("Sprinting (Toggled)"), "Ground sprint text regressed");
                mc.player.abilities.flying = true;
                result.put("flyingHudAndToggle", true);
                TabBackgroundConfig.setEnabled(true);
                TabBackgroundConfig.setBackgroundColor(new Color(0x123456));
                TabBackgroundConfig.setOpacity(25);
                TabBackgroundConfig.setOwnNameColor(new Color(0xAB3020));
                TabBackgroundConfig.setOwnNameOpacity(75);
                TabBackgroundConfig.setHighlightOwnName(false);
                stage = 2;
            }
        } catch (Throwable error) { finish(error); }
    }

    public static void frame() {
        if (done || stage < 2 || ++frames < 8) return;
        var mc = Minecraft.getInstance();
        try {
            colors.clear(); recording = true;
            net.minecraft.client.gui.GuiElement.fill(0, 0, 1, 1, 0xFFFF00FF);
            result.put("fillProbe", List.copyOf(colors));
            colors.clear();
            result.put("players", mc.getNetworkHandler().getOnlinePlayers().size());
            result.put("configuredColor", String.format("%08x", TabBackgroundConfig.background(0, false)));
            mc.gui.getPlayerTabOverlay().render(mc.width / 2, mc.world.getScoreboard(), null);
            recording = false;
            var captured = List.copyOf(colors);
            result.put("tabStage" + stage, captured.stream().map(value -> String.format("%08x", value)).toList());
            if (stage == 2) {
                Screenshot.take(mc.gameDir, "release058-tab-diagnostic.png", mc.width, mc.height, mc.getRenderTarget());
                require(Collections.frequency(captured, 0x40123456) >= 3 && !captured.contains(0xBFAB3020), "Uniform tab color failed");
                TabBackgroundConfig.setHighlightOwnName(true);
            } else if (stage == 3) {
                require(Collections.frequency(captured, 0xBFAB3020) == 1 && Collections.frequency(captured, 0x40123456) >= 2, "Own-name color affected the wrong rows");
                Screenshot.take(mc.gameDir, "release058-tab-and-flying.png", mc.width, mc.height, mc.getRenderTarget());
                TabBackgroundConfig.setOpacity(0);
            } else if (stage == 4) {
                require(captured.equals(List.of(0xBFAB3020)), "Own-name highlight disappeared with a transparent normal background");
                TabBackgroundConfig.setEnabled(false);
            } else {
                require(!captured.contains(0xBFAB3020) && !captured.contains(0x40123456), "Disabled Tab Background still overrides colors");
                result.put("tabColorsAndDisabledFallback", true);
                finish(null);
            }
            frames = 0; stage++;
        } catch (Throwable error) { recording = false; finish(error); }
    }

    private static void finish(Throwable error) {
        done = true; recording = false;
        result.put("passed", error == null);
        if (error != null) { result.put("error", error.toString()); error.printStackTrace(); }
        try { Files.writeString(Path.of(System.getProperty("arcadium.release058.output")), new GsonBuilder().setPrettyPrinting().create().toJson(result)); }
        catch (Exception failure) { failure.printStackTrace(); }
        Minecraft.getInstance().stop();
    }
}
