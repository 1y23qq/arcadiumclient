// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.features;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Abilities;

public final class ToggleSprintController {
    private static final TogglePress SPRINT = new TogglePress(), SNEAK = new TogglePress();
    private static final TogglePress EXTRA_SPRINT = new TogglePress(), EXTRA_SNEAK = new TogglePress();
    private static boolean polling, wasFlying;
    private static Object lastPlayer;

    private ToggleSprintController() {}

    private static boolean playing() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.level != null && mc.screen == null && mc.isWindowActive();
    }

    public static void beforeTick() {
        Minecraft mc = Minecraft.getInstance();
        polling = true;
        try {
            boolean available = playing() && ToggleSprintConfig.isEnabled();
            if (SPRINT.rising(mc.options.keySprint.isDown(), available && ToggleSprintConfig.getToggleSprint() && !ToggleSprintConfig.getKeybindToggleSprint()))
                ToggleSprintConfig.toggleSprintState = !ToggleSprintConfig.toggleSprintState;
            if (SNEAK.rising(mc.options.keyShift.isDown(), available && ToggleSprintConfig.getToggleSneak() && !ToggleSprintConfig.getKeybindToggleSneak()))
                ToggleSprintConfig.toggleSneakState = !ToggleSprintConfig.toggleSneakState;
            if (lastPlayer != mc.player) {
                lastPlayer = mc.player;
                wasFlying = mc.player != null && mc.player.getAbilities().flying;
            }
        } finally { polling = false; }
    }

    public static void afterTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        boolean flying = mc.player.getAbilities().flying;
        if (ToggleSprintConfig.isEnabled() && ToggleSprintConfig.getUnsneakOnFlightStart() && flying && !wasFlying)
            ToggleSprintConfig.toggleSneakState = false;
        wasFlying = flying;
    }

    public static void separateSprint(boolean down) {
        if (EXTRA_SPRINT.rising(down, playing() && ToggleSprintConfig.isEnabled() && ToggleSprintConfig.getToggleSprint() && ToggleSprintConfig.getKeybindToggleSprint()))
            ToggleSprintConfig.toggleSprintState = !ToggleSprintConfig.toggleSprintState;
    }
    public static void separateSneak(boolean down) {
        if (EXTRA_SNEAK.rising(down, playing() && ToggleSprintConfig.isEnabled() && ToggleSprintConfig.getToggleSneak() && ToggleSprintConfig.getKeybindToggleSneak()))
            ToggleSprintConfig.toggleSneakState = !ToggleSprintConfig.toggleSneakState;
    }
    public static boolean sprint(boolean physical) {
        return physical || !polling && playing() && ToggleSprintConfig.isEnabled() && ToggleSprintConfig.getToggleSprint() && ToggleSprintConfig.toggleSprintState;
    }
    public static boolean sneak(boolean physical) {
        return physical || !polling && playing() && ToggleSprintConfig.isEnabled() && ToggleSprintConfig.getToggleSneak() && ToggleSprintConfig.toggleSneakState;
    }
    public static float flight(Abilities abilities, float vanilla) {
        Minecraft mc = Minecraft.getInstance();
        if (!ToggleSprintConfig.isEnabled() || !ToggleSprintConfig.getToggleFlyBoost() || !mc.hasSingleplayerServer()
                || mc.player == null || mc.player.getAbilities() != abilities || !abilities.flying || !sprint(mc.options.keySprint.isDown())) return vanilla;
        float amount = ToggleSprintConfig.getFlyBoostAmount();
        return vanilla * (Float.isFinite(amount) ? Math.max(1, Math.min(10, amount)) : 1);
    }
}
