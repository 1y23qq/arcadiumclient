// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.render;

public final class ArmorVisibility {
    private ArmorVisibility() { }

    public static boolean hidden(int slot, boolean helmet, boolean chestplate, boolean leggings, boolean boots) {
        return switch (slot) {
            case 4 -> helmet;
            case 3 -> chestplate;
            case 2 -> leggings;
            case 1 -> boots;
            default -> false;
        };
    }
}
