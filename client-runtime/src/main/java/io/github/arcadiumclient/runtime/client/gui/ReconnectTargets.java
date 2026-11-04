// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.menu.multiplayer.ConnectScreen;
import net.minecraft.client.options.ServerListEntry;

public final class ReconnectTargets {
    private static volatile Target last;
    private ReconnectTargets() { }
    public static Target last() { return last; }
    public static void remember(ServerListEntry server) { last = new Target(server); }
    public static void remember(String host, int port) {
        String address = host.indexOf(':') >= 0 && !host.startsWith("[") ? "[" + host + "]" : host;
        remember(new ServerListEntry("Server", port == 25565 ? address : address + ":" + port, false));
    }
    public static void clear() { last = null; }
    public static void forget(Target target) { if (last == target) last = null; }
    public record Target(ServerListEntry server) {
        public void connect(Screen parent) {
            Minecraft client = Minecraft.getInstance();
            client.openScreen(new ConnectScreen(parent, client, server));
        }
    }
}
