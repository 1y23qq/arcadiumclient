// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

public final class RenderHookAllocationControl {
    public static volatile boolean enabled;
    public static volatile int fusedSites;
    public static volatile int particleSites;
    public static volatile int layerSites;
    public static volatile int cosmeticSites;
    public static volatile int nameSites;

    private RenderHookAllocationControl() { }

    public static String status() {
        return enabled ? "Render hooks: " + fusedSites + " matrix / " + particleSites + " particle / " + layerSites + " layer / " + cosmeticSites + " cosmetic / " + nameSites + " name gates" : "Render hook allocations: Original";
    }
}
