// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features;

public final class BadlionAnimationContext {
    private static final ThreadLocal<Integer> INVENTORY_DEPTH = ThreadLocal.withInitial(() -> 0);

    private BadlionAnimationContext() {}

    public static void enterInventory() { INVENTORY_DEPTH.set(INVENTORY_DEPTH.get() + 1); }
    public static void exitInventory() {
        int next = INVENTORY_DEPTH.get() - 1;
        if (next <= 0) INVENTORY_DEPTH.remove();
        else INVENTORY_DEPTH.set(next);
    }
    public static boolean inInventory() { return INVENTORY_DEPTH.get() > 0; }
}
