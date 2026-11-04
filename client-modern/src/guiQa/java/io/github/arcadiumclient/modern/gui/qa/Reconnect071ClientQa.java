// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.gui.qa;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.modern.client.ClientPreferences;
import io.github.arcadiumclient.modern.gui.*;
import io.github.arcadiumclient.shared.qa.ReconnectScenario;
import java.nio.file.*;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.polyfrost.oneconfig.api.platform.v1.Platform;
import org.polyfrost.oneconfig.internal.OneConfigConfig;

public final class Reconnect071ClientQa implements ClientModInitializer {
    private ReconnectScenario scenario;
    private int ticks;
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try {
                if (scenario != null) { scenario.tick(); return; }
                Screen current = Platform.screen().current();
                if (++ticks < 80 || current == null) return;
                if (!(current instanceof ArcadiumMainMenu)) {
                    if (ticks > 600) throw new IllegalStateException("Title screen was not reached: " + current.getClass().getName());
                    return;
                }
                if (!mc.getUser().getName().equals("ArcadiumTest") || !mc.getUser().getAccessToken().equals("0") || mc.level != null) throw new IllegalStateException("Use the isolated offline fixture");
                ClientPreferences.setSetupComplete(true); OneConfigConfig.limitUnfocusedFps = false;
                Screen parent = new ArcadiumServersScreen(new TitleScreen());
                scenario = new ReconnectScenario(new ReconnectScenario.Client() {
                    public Object screen() { return Platform.screen().current(); }
                    public boolean disconnected(Object screen) { return screen instanceof DisconnectedScreen; }
                    public boolean atParent() { return screen() == parent; }
                    public void connect(int port, boolean raw) { String ip = "127.0.0.1:" + port; ConnectScreen.startConnecting(parent, mc, ServerAddress.parseString(ip), new ServerData(raw ? "Direct address" : "Saved entry", ip, ServerData.Type.OTHER), false, null); }
                    public Object reconnectButton(Object screen) { return ((Screen) screen).children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(button -> button.getMessage().getString().startsWith("Reconnect")).findFirst().orElse(null); }
                    public Object backButton(Object screen) { Object retry = reconnectButton(screen); return ((Screen) screen).children().stream().filter(Button.class::isInstance).filter(button -> button != retry).findFirst().orElse(null); }
                    public int buttonY(Object button) { return ((Button) button).getY(); }
                    public String buttonText(Object button) { return ((Button) button).getMessage().getString(); }
                    public void click(Object screen, Object button) { ((Button) button).onPress(new MouseButtonInfo(0, 0)); }
                    public Object target() { return ReconnectTargets.last(); }
                    public String address() { return ReconnectTargets.last().server().ip; }
                    public void reinitialize(Object screen) { Screen value = (Screen) screen; value.resize(value.width, value.height); }
                    public void finish(Map<String, Object> report, Throwable failure) { save(mc, report, failure); }
                });
            } catch (Throwable failure) { save(mc, Map.of("passed", false, "failure", failure.toString()), failure); }
        });
    }
    private static void save(Minecraft mc, Map<String, Object> report, Throwable failure) {
        if (failure != null) failure.printStackTrace();
        try { Path file = Path.of(System.getProperty("arcadium.reconnect071.output")); Files.createDirectories(file.getParent()); Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception error) { error.printStackTrace(); }
        mc.stop();
    }
}
