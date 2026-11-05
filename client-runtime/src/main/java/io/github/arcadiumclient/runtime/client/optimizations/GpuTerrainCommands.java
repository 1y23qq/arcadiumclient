// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

public final class GpuTerrainCommands {
    public static volatile boolean enabled;
    public static long templateUploads, visibilityUploads, dispatches, commandsReused, regionDraws, regionsCreated, fallbacks;
    private static volatile Backend backend;
    private GpuTerrainCommands() {}

    interface Backend {
        String status();
        long residentBytes();
        void reset();
    }
    static void attach(Backend value) { backend = value; }
    public static long residentBytes() { return backend == null ? 0 : backend.residentBytes(); }
    public static void reset() { if (backend != null) backend.reset(); }
    public static String status() {
        if (!enabled) return "GPU terrain commands: Off";
        return backend == null ? "GPU terrain commands: Waiting for Argentum terrain" : backend.status();
    }
}
