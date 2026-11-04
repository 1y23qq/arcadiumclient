// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.runtime.client.ClientPreferences;
import io.github.arcadiumclient.runtime.client.gui.ArcadiumServersScreen;
import io.github.arcadiumclient.runtime.client.gui.ReconnectTargets;
import io.github.arcadiumclient.runtime.mixin.client.access.ScreenAccess;
import io.github.arcadiumclient.runtime.qa.mixin.ReconnectScreen071Access;
import io.github.arcadiumclient.shared.qa.ReconnectScenario;
import java.nio.file.*;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.menu.TitleScreen;
import net.minecraft.client.gui.screen.menu.multiplayer.*;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.options.ServerListEntry;
import org.polyfrost.oneconfig.internal.OneConfigConfig;

public final class Reconnect071Qa {
    private static int ticks;
    private static ReconnectScenario scenario;
    public static void tick() {
        if (System.getProperty("arcadium.reconnect071.output") == null) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (scenario != null) { scenario.tick(); return; }
            if (++ticks < 80 || mc.screen == null) return;
            if (!mc.getSession().getUsername().equals("ArcadiumTest") || !mc.getSession().getAccessToken().equals("0") || mc.world != null) throw new IllegalStateException("Use the isolated offline fixture");
            ClientPreferences.setSetupComplete(true); OneConfigConfig.limitUnfocusedFps = false;
            Screen parent = new ArcadiumServersScreen(new TitleScreen());
            scenario = new ReconnectScenario(new ReconnectScenario.Client() {
                public Object screen() { return mc.screen; }
                public boolean disconnected(Object screen) { return screen instanceof DisconnectedScreen; }
                public boolean atParent() { return mc.screen == parent; }
                public void connect(int port, boolean raw) { mc.openScreen(raw ? new ConnectScreen(parent, mc, "127.0.0.1", port) : new ConnectScreen(parent, mc, new ServerListEntry("Reconnect QA", "127.0.0.1:" + port, false))); }
                public Object reconnectButton(Object screen) { return ((ScreenAccess) screen).arcadium$buttons().stream().filter(button -> button.id == 37071).findFirst().orElse(null); }
                public Object backButton(Object screen) { return ((ScreenAccess) screen).arcadium$buttons().stream().filter(button -> button.id == 0).findFirst().orElse(null); }
                public int buttonY(Object button) { return ((ButtonWidget) button).y; }
                public String buttonText(Object button) { return ((ButtonWidget) button).message; }
                public void click(Object screen, Object button) { ((ReconnectScreen071Access) screen).arcadium$clickReconnect071((ButtonWidget) button); }
                public Object target() { return ReconnectTargets.last(); }
                public String address() { return ReconnectTargets.last().server().ip; }
                public void reinitialize(Object screen) { Screen value = (Screen) screen; value.init(mc, value.width, value.height); }
                public void capture(String name) { Screenshot.take(mc.gameDir, name + ".png", mc.width, mc.height, mc.getRenderTarget()); }
                public void finish(Map<String, Object> report, Throwable failure) { save(report, failure); }
            });
        } catch (Throwable failure) { save(Map.of("passed", false, "failure", failure.toString()), failure); }
    }
    private static void save(Map<String, Object> report, Throwable failure) {
        if (failure != null) failure.printStackTrace();
        try { Path file = Path.of(System.getProperty("arcadium.reconnect071.output")); Files.createDirectories(file.getParent()); Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception error) { error.printStackTrace(); }
        Minecraft.getInstance().stop();
    }
}
