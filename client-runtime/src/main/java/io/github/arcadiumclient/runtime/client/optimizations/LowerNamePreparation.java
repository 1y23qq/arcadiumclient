// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

public final class LowerNamePreparation {
    private static final boolean BOOT = Boolean.getBoolean("arcadium.namePreparation073.lowerEntity");
    public static int preparedSites;
    public static long typedCalls;

    private LowerNamePreparation() { }
    public static boolean active(Object owner) {
        return BOOT && preparedSites == 1 && RenderHookAllocationControl.enabled && HologramNamePreparation.active(owner);
    }
    public static void noteTyped() { typedCalls++; }
}
