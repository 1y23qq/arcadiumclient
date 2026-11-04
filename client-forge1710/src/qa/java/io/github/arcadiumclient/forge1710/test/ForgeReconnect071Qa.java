// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710.test;

import com.google.gson.GsonBuilder;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import io.github.arcadiumclient.runtime.client.ClientPreferences;
import io.github.arcadiumclient.runtime.client.gui.ArcadiumMainMenu;
import io.github.arcadiumclient.runtime.client.gui.ArcadiumServersScreen;
import io.github.arcadiumclient.runtime.client.gui.ReconnectTargets;
import io.github.arcadiumclient.shared.qa.ReconnectScenario;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.multiplayer.*;
import org.polyfrost.oneconfig.internal.OneConfigConfig;

public final class ForgeReconnect071Qa {
    private int ticks;
    private ReconnectScenario scenario;
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.func_71410_x();
        try {
            if (scenario != null) { scenario.tick(); return; }
            if (++ticks < 80 || mc.field_71462_r == null) return;
            if (!mc.func_110432_I().func_111285_a().equals("ArcadiumTest") || !mc.func_110432_I().func_148254_d().equals("0") || mc.field_71441_e != null) throw new IllegalStateException("Use the isolated offline fixture");
            ClientPreferences.setSetupComplete(true); OneConfigConfig.limitUnfocusedFps = false;
            GuiScreen parent = new ArcadiumServersScreen(new ArcadiumMainMenu());
            scenario = new ReconnectScenario(new ReconnectScenario.Client() {
                public Object screen() { return mc.field_71462_r; }
                public boolean disconnected(Object screen) { return screen instanceof GuiDisconnected; }
                public boolean atParent() { return mc.field_71462_r == parent; }
                public void connect(int port, boolean raw) { mc.func_147108_a(raw ? new GuiConnecting(parent, mc, "127.0.0.1", port) : new GuiConnecting(parent, mc, new ServerData("Reconnect QA", "127.0.0.1:" + port))); }
                public Object reconnectButton(Object screen) { return buttons(screen).stream().filter(button -> button.field_146127_k == 37071).findFirst().orElse(null); }
                public Object backButton(Object screen) { return buttons(screen).stream().filter(button -> button.field_146127_k == 0).findFirst().orElse(null); }
                public int buttonY(Object button) { return ((GuiButton) button).field_146129_i; }
                public String buttonText(Object button) { return ((GuiButton) button).field_146126_j; }
                public void click(Object screen, Object button) throws Exception { var method = GuiScreen.class.getDeclaredMethod("func_146284_a", GuiButton.class); method.setAccessible(true); method.invoke(screen, button); }
                public Object target() { return ReconnectTargets.last(); }
                public String address() { return ReconnectTargets.last().server().field_78845_b; }
                public void reinitialize(Object screen) { GuiScreen value = (GuiScreen) screen; value.func_146280_a(mc, value.field_146294_l, value.field_146295_m); }
                public boolean legacyLoginDisconnect() { return true; }
                public void finish(Map<String, Object> report, Throwable failure) { save(mc, report, failure); }
            });
        } catch (Throwable failure) { save(mc, Map.of("passed", false, "failure", failure.toString()), failure); }
    }
    @SuppressWarnings("unchecked") private static List<GuiButton> buttons(Object screen) {
        try { var field = GuiScreen.class.getDeclaredField("field_146292_n"); field.setAccessible(true); return (List<GuiButton>) field.get(screen); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
    private static void save(Minecraft mc, Map<String, Object> report, Throwable failure) {
        if (failure != null) failure.printStackTrace();
        try { Path file = Path.of(System.getProperty("arcadium.reconnect071.output")); Files.createDirectories(file.getParent()); Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception error) { error.printStackTrace(); }
        mc.func_71400_g();
    }
}
