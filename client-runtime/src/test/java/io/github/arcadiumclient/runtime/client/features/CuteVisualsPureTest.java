// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features;

import io.github.arcadiumclient.runtime.client.render.ArmorVisibility;

public final class CuteVisualsPureTest {
    private static CuteVisualsState.Options options(boolean trail, boolean bursts) {
        return new CuteVisualsState.Options(trail, trail, true, false, bursts, bursts,
                200, 1500, 100, 1500, 20, 1500, .2f, 2.5f, 3000, 5, .85f);
    }

    public static void main(String[] args) {
        CuteVisualsState state = new CuteVisualsState(42);
        CuteVisualsState.Options settings = options(true, true);
        state.tick(0, 0, 64, 0, settings);
        check(state.count() == 0, "A stationary player does not emit moving-only trails");
        state.tick(50, .1, 64, 0, settings);
        check(state.count() == 2, "Movement emits both enabled trail types");
        state.tick(100, .2, 64, 0, settings);
        check(state.count() == 2, "Emission intervals are independent from rendered frame count");
        state.tick(2000, .2, 64, 0, settings);
        check(state.count() == 0, "Effects expire during ticks even when never rendered");
        for (int i = 0; i < 2000; i++) state.burst(3000 + i, 0, 64, 0, 1, 1, settings);
        check(state.count() <= CuteVisualsState.CAPACITY, "Repeated break events have bounded memory");
        check(state.count(CuteVisualsState.Kind.RAINBOW) <= 8, "Repeated break events cannot flood the renderer with ribbon geometry");
        state.tick(6000, 0, 64, 0, options(false, false));
        check(state.count() == 0, "Disabling individual effects immediately clears their visuals");
        check(!state.dig(CuteVisualsState.Dig.BEGIN, 1, 64, 1, true, false), "Survival break waits for completion");
        check(!state.dig(CuteVisualsState.Dig.CANCEL, 1, 64, 1, true, false), "Cancelled mining produces no effect");
        check(!state.dig(CuteVisualsState.Dig.FINISH, 1, 64, 1, false, false), "A cancelled break cannot finish later");
        state.dig(CuteVisualsState.Dig.BEGIN, 1, 64, 1, true, false);
        check(!state.dig(CuteVisualsState.Dig.FINISH, 2, 64, 1, true, false), "Different block completion cannot trigger a bed effect");
        state.dig(CuteVisualsState.Dig.BEGIN, 1, 64, 1, true, false);
        check(state.dig(CuteVisualsState.Dig.FINISH, 1, 64, 1, false, false), "A completed break remains valid after local block removal");
        check(state.dig(CuteVisualsState.Dig.BEGIN, 1, 64, 1, true, true), "Creative bed breaks trigger immediately");
        check(!state.dig(CuteVisualsState.Dig.FINISH, 1, 64, 1, false, true), "Creative break does not trigger twice");
        state.burst(7000, 0, 64, 0, 1, 1, settings);
        state.tick(10, 0, 64, 0, settings);
        check(state.count() == 0, "A backwards clock or new-world epoch clears stale effects");
        state.tick(20, Double.NaN, 0, 0, settings);
        state.tick(30, 100000, 64, 0, settings);
        check(state.count() == 0, "Invalid coordinates cannot create a trail across a teleport");
        for (int flags = 0; flags < 16; flags++) {
            for (int slot = 1; slot <= 4; slot++) {
                boolean hidden = ArmorVisibility.hidden(slot, (flags & 8) != 0, (flags & 4) != 0,
                        (flags & 2) != 0, (flags & 1) != 0);
                check(hidden == ((flags & 1 << (slot - 1)) != 0), "Armor preferences match the actual vanilla slot number");
            }
            check(!ArmorVisibility.hidden(0, true, true, true, true), "Held items are never hidden by armor preferences");
        }
        for (int i = -100; i <= 1100; i++) {
            float value = CuteVisualsState.opacity(i / 1000f);
            check(Float.isFinite(value) && value >= 0 && value <= 1, "Fade opacity remains finite and bounded");
        }
        System.out.println("Independent visual state and armor slot checks passed");
    }

    private static void check(boolean success, String message) {
        if (!success) throw new AssertionError(message);
    }
}
