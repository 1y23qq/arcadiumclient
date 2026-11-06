// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

public final class GpuTerrainCommands {
    public static volatile boolean enabled;
    public static long templateUploads, visibilityUploads, dispatches, commandsReused, regionDraws, regionsCreated, fallbacks;
    private static volatile Backend backend, residentBackend;
    private static final boolean residentBootRequested=Boolean.getBoolean("arcadium.terrainResident072.enabled");
    private GpuTerrainCommands() {}

    interface Backend {
        String status();
        long residentBytes();
        void reset();
        default void shutdown() { reset(); }
        default boolean residentSceneLoaded() { return false; }
    }
    static void attach(Backend value) { backend = value; }
    static synchronized void attachResident(Backend value) { residentBackend = value; }
    static synchronized void attachResidentIfAbsent(Backend value) { if(residentBackend==null)residentBackend=value; }
    public static boolean residentRequested() { return enabled&&residentBootRequested; }
    // Passive shader facts are not a loaded scene.
    public static boolean residentAttached() { Backend value=residentBackend; return value!=null&&value.residentSceneLoaded(); }
    public static boolean residentBootRequested() { return residentBootRequested; }
    public static long residentBytes() { Backend a=backend,b=residentBackend; return (a==null?0:a.residentBytes())+(b==null?0:b.residentBytes()); }
    public static void reset() {
        Backend a=backend,b=residentBackend;
        try { if(a!=null)a.reset(); }
        catch(RuntimeException | Error failure) {
            try { if(b!=null)b.reset(); } catch(RuntimeException | Error secondary) { if(secondary!=failure)failure.addSuppressed(secondary); }
            throw failure;
        }
        if(b!=null)b.reset();
    }
    public static void shutdown() {
        Backend a=backend,b=residentBackend;
        try { if(a!=null)a.shutdown(); }
        catch(RuntimeException | Error failure) {
            try { if(b!=null)b.shutdown(); } catch(RuntimeException | Error secondary) { if(secondary!=failure)failure.addSuppressed(secondary); }
            throw failure;
        }
        if(b!=null)b.shutdown();
    }
    public static String status() {
        if (!enabled) return "GPU terrain commands: Off";
        Backend resident=residentBackend;
        if(resident!=null&&residentBootRequested)return resident.status();
        return backend == null ? "GPU terrain commands: Waiting for Argentum terrain" : backend.status();
    }
}
