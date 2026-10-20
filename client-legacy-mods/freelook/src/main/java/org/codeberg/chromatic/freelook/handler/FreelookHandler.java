package org.codeberg.chromatic.freelook.handler;

import org.codeberg.chromatic.freelook.option.FreelookConfig;
import org.codeberg.chromatic.freelook.util.CameraController;
import org.codeberg.chromatic.freelook.util.CameraStateTracker;
import org.codeberg.chromatic.freelook.util.EasingProgressTimer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
//? if <=1.8.9
import net.minecraft.world.entity.Entity;
import org.polyfrost.oneconfig.api.hypixel.v1.HypixelUtils;

public class FreelookHandler {

    public static final FreelookHandler INSTANCE = new FreelookHandler();

    public boolean freelookToggled = false;

    public boolean enabledServer = true;

    int lastPerspective = 0;
    private long pressStartTime = 0;
    private boolean keyDown = false;
    private boolean wasKeyDown = false;
    final EasingProgressTimer timer = new EasingProgressTimer(650L);

    public void tick(Minecraft minecraft) {
        boolean pressed = keyDown && !wasKeyDown;
        wasKeyDown = keyDown;

        if (!enabledServer || HypixelUtils.isHypixel()) {
            stop();
            return;
        }

        handlePerspectiveChange();

        switch (FreelookConfig.pressMode) {
            case 1 -> handlePressAndHold(keyDown);
            case 2 -> {
                if (pressed) toggle();
            }
            case 0 -> {
                if (keyDown) {
                    start();
                } else {
                    stop();
                }
            }
        }
    }

    private void handlePerspectiveChange() {
        int perspective = CameraController.get();
        if (!freelookToggled || perspective == CameraController.activePerspective) return;

        switch (FreelookConfig.onCycleChange) {
            case 0 -> CameraController.activePerspective = perspective;
            case 1 -> {
                stop();
                CameraController.restore(perspective);
            }
            case 2 -> CameraController.restore(CameraController.activePerspective);
        }
    }

    public void setKeyDown(boolean down) {
        keyDown = down;
    }

    public void handlePressAndHold(boolean pressed) {
        if (pressed && pressStartTime == 0L) {
            pressStartTime = System.currentTimeMillis();
            toggle();
        } else if (!pressed && pressStartTime != 0L) {
            long pressDuration = System.currentTimeMillis() - pressStartTime;
            pressStartTime = 0L;

            if (pressDuration > FreelookConfig.holdThreshold) {
                stop();
            }
        }
    }

    public void toggle() {
        if (freelookToggled) {
            stop();
        } else {
            start();
        }
    }

    public void start() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        if (freelookToggled) return;

        lastPerspective = CameraController.get();

        CameraController.set(FreelookConfig.perspectiveType);

        timer.start();

        if (player instanceof CameraStateTracker tracker) {
            //? if >1.8.9 {
            /*tracker.freelook$setPitch(player.getXRot());
            tracker.freelook$setYaw(player.getYRot());
            *///?} else {
            tracker.freelook$setPitch(player.pitch);
            tracker.freelook$setYaw(player.yaw);
            //?}
        }

        freelookToggled = true;
    }

    public void stop() {
        if (!freelookToggled) return;

        freelookToggled = false;
        CameraCycleHandler.hasCycledFreelook = false;
        CameraController.restore(lastPerspective);
        timer.stop();
    }

    public float applySmoothScale(float z) {
        if (!freelookToggled || timer.isComplete() || !FreelookConfig.smoothCamera) return z;

        float transitionProgress = timer.getProgress();
        float scale = 0.125f + transitionProgress * (1.0f - 0.125f);
        return z * scale;
    }

    public boolean useCinematicCamera() {
        return freelookToggled && FreelookConfig.cinematicCamera > 0f;
    }

    public double cinematicSmoothing(double vanilla) {
        if (!useCinematicCamera()) return vanilla;

        double scaled = vanilla * 100.0 / FreelookConfig.cinematicCamera;
        return Math.max(vanilla, Math.min(1.0, scaled));
    }

    //? if <=1.8.9 {
    public float cameraYaw(Entity entity, float vanilla) {
        return freelookToggled && entity instanceof CameraStateTracker tracker ? tracker.freelook$getYaw() : vanilla;
    }

    public float cameraPitch(Entity entity, float vanilla) {
        return freelookToggled && entity instanceof CameraStateTracker tracker ? tracker.freelook$getPitch() : vanilla;
    }
    //?}

    public static float calculateCameraRotation(float currentValue, double delta, boolean invert, boolean lock) {
        delta = (delta * 0.15F * (FreelookConfig.sensitivity / 100.0F) * (invert ? -1 : 1));
        float rotation = currentValue + (float) delta;
        return lock ? Mth.clamp(rotation, -90.0F, 90.0F) : rotation;
    }

}
