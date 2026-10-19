// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.options.GameOptions;
import net.minecraft.entity.living.player.PlayerAbilities;

public final class ToggleSprintController {
    private static final TogglePress SPRINT = new TogglePress(), SNEAK = new TogglePress();
    private static final TogglePress EXTRA_SPRINT = new TogglePress(), EXTRA_SNEAK = new TogglePress();
    private static LocalClientPlayerEntity lastPlayer;
    private static boolean wasFlying;

    private ToggleSprintController() { }

    private static boolean playing() {
        Minecraft client = Minecraft.getInstance();
        return client.player != null && client.world != null && client.screen == null && client.focused;
    }

    public static void beforeTick() {
        Minecraft client = Minecraft.getInstance();
        if (client.options == null) return;
        boolean available = playing() && ToggleSprintConfig.isEnabled();
        boolean sprint = GameOptions.isPressed(client.options.sprintKey);
        boolean sneak = GameOptions.isPressed(client.options.sneakKey);
        if (SPRINT.rising(sprint, available && ToggleSprintConfig.getToggleSprint() && !ToggleSprintConfig.getKeybindToggleSprint()))
            ToggleSprintConfig.toggleSprintState = !ToggleSprintConfig.toggleSprintState;
        if (SNEAK.rising(sneak, available && ToggleSprintConfig.getToggleSneak() && !ToggleSprintConfig.getKeybindToggleSneak()))
            ToggleSprintConfig.toggleSneakState = !ToggleSprintConfig.toggleSneakState;
        if (lastPlayer != client.player) {
            lastPlayer = client.player;
            wasFlying = client.player != null && client.player.abilities.flying;
        }
    }

    public static void afterTick() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        boolean flying = client.player.abilities.flying;
        if (ToggleSprintConfig.isEnabled() && ToggleSprintConfig.getUnsneakOnFlightStart() && flying && !wasFlying)
            ToggleSprintConfig.toggleSneakState = false;
        wasFlying = flying;
    }

    public static void separateSprint(boolean down) {
        if (EXTRA_SPRINT.rising(down, playing() && ToggleSprintConfig.isEnabled() && ToggleSprintConfig.getToggleSprint()
                && ToggleSprintConfig.getKeybindToggleSprint())) ToggleSprintConfig.toggleSprintState = !ToggleSprintConfig.toggleSprintState;
    }

    public static void separateSneak(boolean down) {
        if (EXTRA_SNEAK.rising(down, playing() && ToggleSprintConfig.isEnabled() && ToggleSprintConfig.getToggleSneak()
                && ToggleSprintConfig.getKeybindToggleSneak())) ToggleSprintConfig.toggleSneakState = !ToggleSprintConfig.toggleSneakState;
    }

    public static boolean sprint(boolean physical) {
        return physical || playing() && ToggleSprintConfig.isEnabled() && ToggleSprintConfig.getToggleSprint() && ToggleSprintConfig.toggleSprintState;
    }

    public static boolean sneak(boolean physical) {
        return physical || playing() && ToggleSprintConfig.isEnabled() && ToggleSprintConfig.getToggleSneak() && ToggleSprintConfig.toggleSneakState;
    }

    public static float flight(PlayerAbilities abilities, float vanilla) {
        Minecraft client = Minecraft.getInstance();
        if (!ToggleSprintConfig.isEnabled() || !ToggleSprintConfig.getToggleFlyBoost() || !client.isSingleplayer()
                || client.player == null || client.player.abilities != abilities || !abilities.flying
                || !sprint(client.options.sprintKey.isPressed())) return vanilla;
        float multiplier = ToggleSprintConfig.getFlyBoostAmount();
        return vanilla * (Float.isFinite(multiplier) ? Math.max(1f, Math.min(10f, multiplier)) : 1f);
    }
}
