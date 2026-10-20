// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.legacy;

import net.minecraft.client.Minecraft;

public final class LegacyGameModes {
    private LegacyGameModes() {}
    public static boolean isSpectator() {
        var player = Minecraft.getInstance().player;
        return player != null && player.isInvisible() && player.abilities.flying && !player.abilities.creativeMode;
    }
}
