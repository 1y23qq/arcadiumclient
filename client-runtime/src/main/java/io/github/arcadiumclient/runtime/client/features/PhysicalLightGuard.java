// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features;

public final class PhysicalLightGuard {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private PhysicalLightGuard() {}

    public static void begin() { DEPTH.set(DEPTH.get() + 1); }
    public static void end() {
        int next = DEPTH.get() - 1;
        if (next <= 0) DEPTH.remove();
        else DEPTH.set(next);
    }
    public static boolean active() { return DEPTH.get() > 0; }
}
