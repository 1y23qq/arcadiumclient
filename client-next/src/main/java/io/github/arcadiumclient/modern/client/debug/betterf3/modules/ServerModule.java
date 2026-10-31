// SPDX-License-Identifier: MIT
// Copyright (c) 2020-2026 cominixo / TreyRuffy. Adapted for Arcadium.
package io.github.arcadiumclient.modern.client.debug.betterf3.modules;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import io.github.arcadiumclient.modern.client.debug.betterf3.utils.DebugLine;
import io.github.arcadiumclient.modern.client.debug.betterf3.utils.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.TickRateManager;

/**
 * The Server module.
 */
public class ServerModule extends BaseModule {

    /**
     * Instantiates a new Server module.
     */
    public ServerModule() {
        this.defaultNameColor = legacyColor(ChatFormatting.GRAY);
        this.defaultValueColor = legacyColor(ChatFormatting.YELLOW);

        this.nameColor = defaultNameColor;
        this.valueColor = defaultValueColor;

        lines.add(new DebugLine("server_tick", "format.betterf3.server_tick", true));
        lines.add(new DebugLine("packets_sent"));
        lines.add(new DebugLine("packets_received"));
        lines.add(new DebugLine("tick_manager_status"));

        for (final DebugLine line : lines) {
            line.inReducedDebug = true;
        }
    }

    /**
     * Updates the Server module.
     *
     * @param client the Minecraft client
     */
    public void update(final Minecraft client) {
        final IntegratedServer integratedServer = client.getSingleplayerServer();

        String serverString = "";
        final ClientPacketListener connection = client.getConnection();
        if (integratedServer != null) {
            serverString = I18n.get("text.betterf3.line.integrated_server");
        } else if (client.player != null) {
            final String serverBrand = Objects.requireNonNull(connection).serverBrand();
            if (serverBrand != null) {
                serverString = serverBrand;
            }
        }

        if (connection != null) {
            final Connection clientConnection = connection.getConnection();
            final float packetsSent = clientConnection.getAverageSentPackets();
            final float packetsReceived = clientConnection.getAverageReceivedPackets();

            lines.get(1).value(Math.round(packetsSent));
            lines.get(2).value(Math.round(packetsReceived));
        }
        String tickString = "";
        if (integratedServer != null) {
            tickString = Integer.toString(Math.round(integratedServer.getCurrentSmoothedTickTime()));
        }

        final List<MutableComponent> serverStringList = new LinkedList<>(
                Arrays.asList(Utils.styledText(serverString, nameColor), Utils.styledText(tickString, nameColor)));

        if (tickString.isEmpty()) {
            lines.getFirst().format("format.betterf3.no_format");
            serverStringList.remove(1);
        }

        lines.get(0).value(serverStringList);

        String tickManagerStatus = I18n.get("text.betterf3.line.tick_manager_status.normal");
        if (client.level != null) {
            final TickRateManager tickratemanager = client.level.tickRateManager();
            if (integratedServer != null && integratedServer.tickRateManager().isSprinting()) {
                tickManagerStatus = I18n.get("text.betterf3.line.tick_manager_status.sprinting");
            } else if (tickratemanager.isSteppingForward()) {
                tickManagerStatus = I18n.get("text.betterf3.line.tick_manager_status.stepping");
            } else if (tickratemanager.isFrozen()) {
                tickManagerStatus = I18n.get("text.betterf3.line.tick_manager_status.frozen");
            }
            lines.get(3).value(tickManagerStatus);
        } else {
            lines.get(3).active = false;
        }
    }
}
