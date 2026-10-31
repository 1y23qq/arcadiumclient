// SPDX-License-Identifier: MIT
// Copyright (c) 2020-2026 cominixo / TreyRuffy. Adapted for Arcadium.
package io.github.arcadiumclient.modern.client.debug.betterf3.modules;

import io.github.arcadiumclient.modern.client.debug.betterf3.utils.DebugLine;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.ClientBrandRetriever;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.TextColor;

/**
 * The Minecraft module.
 */
public class MinecraftModule extends BaseModule {

    /**
     * Instantiates a new Minecraft module.
     */
    public MinecraftModule() {
        defaultNameColor = TextColor.fromRgb(0xA0522D);
        defaultValueColor = legacyColor(ChatFormatting.DARK_GREEN);

        this.nameColor = defaultNameColor;
        this.valueColor = defaultValueColor;

        lines.add(new DebugLine("minecraft", "format.betterf3.default_no_colon", false));
        lines.getFirst().inReducedDebug = true;
    }

    /**
     * Updates the Minecraft module.
     *
     * @param client the Minecraft client
     */
    public void update(final Minecraft client) {
        lines.getFirst()
                .value(SharedConstants.getCurrentVersion().name() + " (" + client.getLaunchedVersion() + "/"
                        + ClientBrandRetriever.getClientModName()
                        + ")");
    }

    @Override
    public boolean updatesEveryFrame() {
        return false;
    }
}
