// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

public final class ReconnectTargets {
    private static volatile Target last;
    private ReconnectTargets() { }
    public static Target last() { return last; }
    public static void remember(ServerData server) { last = server == null ? null : new Target(server); }
    public static void clear() { last = null; }
    public static void forget(Target target) { if (last == target) last = null; }
    public record Target(ServerData server) {
        public void connect(Screen parent) {
            ConnectScreen.startConnecting(parent, Minecraft.getInstance(), ServerAddress.parseString(server.ip), server, false, null);
        }
    }
}
