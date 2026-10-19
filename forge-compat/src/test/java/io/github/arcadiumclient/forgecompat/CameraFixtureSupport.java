package io.github.arcadiumclient.forgecompat;

public final class CameraFixtureSupport {
    private CameraFixtureSupport() { }
    public static class Entity { public float yaw = 1, previousYaw = 2, pitch = 3, previousPitch = 4, nativeYaw = 101, nativePitch = 202; }
    public static class Player extends Entity { }
    public static final class Vector { public final float value; public Vector(float value) { this.value = value; } }
    public static final class Game { public Entity view; public Game(Entity view) { this.view = view; } public Entity getRenderViewEntity() { return view; } }
    public static final class GuestManager { public static GuestFreelook freelook; public static GuestAim aimAssist; public static GuestHurt noHurtCam; }
    public static final class GuestFreelook {
        public static boolean perspectiveToggled;
        public static float cameraYaw = 303, cameraPitch = 404;
        public boolean enabled; public int checks;
        public boolean isEnabled() { checks++; return enabled; }
    }
    public static final class GuestAim {
        public boolean active; public int checks, yawReads, previousYawReads, pitchReads, previousPitchReads;
        public RuntimeException failure;
        public boolean isDetachedCameraActive() { checks++; if (failure != null) throw failure; return active; }
        public float getCameraYaw() { yawReads++; return 505; }
        public float getPrevCameraYaw() { previousYawReads++; return 506; }
        public float getCameraPitch() { pitchReads++; return 607; }
        public float getPrevCameraPitch() { previousPitchReads++; return 608; }
    }
    public static final class GuestHurt {
        public boolean enabled; public int checks; public RuntimeException failure;
        public Slider multiplier = new Slider();
        public boolean isEnabled() { checks++; if (failure != null) throw failure; return enabled; }
    }
    public static final class Slider { public double value; public int reads; public double getInput() { reads++; return value; } }
    public static final class Gl {
        public static final java.util.List<float[]> rotations = new java.util.ArrayList<>();
        public static RuntimeException failure;
        public static void rotate(float angle, float x, float y, float z) { if (failure != null) throw failure; rotations.add(new float[]{angle, x, y, z}); }
    }
    public static int nativeYawReads, nativePitchReads;
    public static int nativeHurtEnabledReads, nativeHurtTypeReads, nativeHurtMultiplierReads;
    public static RuntimeException nativeFailure, nativeHurtFailure;
    public static float nativeYaw(Entity entity) { nativeYawReads++; if (nativeFailure != null) throw nativeFailure; return entity.nativeYaw; }
    public static float nativePitch(Entity entity) { nativePitchReads++; if (nativeFailure != null) throw nativeFailure; return entity.nativePitch; }
    public static void countHurtEnabled() { nativeHurtEnabledReads++; }
    public static void countHurtType() { nativeHurtTypeReads++; }
    public static void countHurtMultiplier() { nativeHurtMultiplierReads++; if (nativeHurtFailure != null) throw nativeHurtFailure; }
    public static void reset() { GuestManager.freelook = null; GuestManager.aimAssist = null; GuestManager.noHurtCam = null; GuestFreelook.perspectiveToggled = false; nativeYawReads = nativePitchReads = 0; nativeFailure = null; nativeHurtEnabledReads = nativeHurtTypeReads = nativeHurtMultiplierReads = 0; nativeHurtFailure = null; Gl.rotations.clear(); Gl.failure = null; }
}
