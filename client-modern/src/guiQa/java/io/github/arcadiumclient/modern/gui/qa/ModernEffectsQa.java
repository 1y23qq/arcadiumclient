// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.gui.qa;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.arcadiumclient.modern.client.features.BetterHurtCamConfig;
import io.github.arcadiumclient.modern.client.features.LightLevelConfig;
import io.github.arcadiumclient.modern.client.features.LightLevelOverlay;
import io.github.arcadiumclient.modern.client.features.NoRender;
import io.github.arcadiumclient.modern.client.features.NoRenderConfig;
import io.github.arcadiumclient.modern.client.features.NoRenderPolicy;
import io.github.arcadiumclient.modern.client.features.VignetteConfig;
import io.github.arcadiumclient.modern.gui.qa.mixin.GameRendererQaAccess;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

final class ModernEffectsQa {
    private final Minecraft minecraft;
    private final Path output;
    private final CompletableFuture<Map<String, Object>> completion = new CompletableFuture<>();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final List<String> images = new ArrayList<>();
    private final Map<Field, Boolean> filters = new LinkedHashMap<>();
    private final boolean vignette = VignetteConfig.enabled;
    private final int vignetteType = VignetteConfig.type, strength = VignetteConfig.strength;
    private final boolean[] statuses = {VignetteConfig.Health.enabled, VignetteConfig.Hunger.enabled, VignetteConfig.Air.enabled};
    private final boolean light = LightLevelConfig.getEnabled(), outlines = LightLevelConfig.getOutlines();
    private final int radius = LightLevelConfig.getRadius(), source = LightLevelConfig.getLightSource();
    private final BetterHurtCamConfig hurt = BetterHurtCamConfig.INSTANCE;
    private final boolean hurtEnabled = hurt.enabled;
    private final double hurtMultiplier = hurt.multiplier;
    private final String hurtType = hurt.type;
    private final CameraType camera;
    private final Screen screen;
    private final boolean nativeVignette;
    private final double damageTilt;
    private final long started = System.nanoTime();
    private int ticks, phase;
    private boolean waiting, complete;
    private int[] baselineGround, baselineHand, hiddenHand;
    private double cornerBrightness;

    static CompletableFuture<Map<String, Object>> run(Minecraft minecraft, Path output) {
        var qa = new ModernEffectsQa(minecraft, output);
        qa.start();
        return qa.completion.thenComposeAsync(report -> {
            if (!Boolean.getBoolean("arcadium.guiQa.legacyVisuals")) return CompletableFuture.completedFuture(report);
            return ModernLegacyVisualsQa.run(minecraft, output).thenApply(value -> { report.put("legacyVisuals", value); return report; });
        }, QaTickScheduler.EXECUTOR);
    }

    private ModernEffectsQa(Minecraft minecraft, Path output) {
        this.minecraft = minecraft; this.output = output;
        camera = minecraft.options.getCameraType(); screen = minecraft.screen;
        nativeVignette = minecraft.options.vignette().get(); damageTilt = minecraft.options.damageTiltStrength().get();
    }

    private void start() {
        try {
            for (Field field : NoRenderConfig.class.getDeclaredFields()) if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers()) && field.getType() == boolean.class) {
                field.setAccessible(true); filters.put(field, field.getBoolean(null)); field.setBoolean(null, false);
            }
            NoRender.tick();
            LightLevelConfig.setEnabled(false);
            VignetteConfig.enabled = true; VignetteConfig.type = 0; VignetteConfig.strength = 0;
            VignetteConfig.Health.enabled = VignetteConfig.Hunger.enabled = VignetteConfig.Air.enabled = false;
            minecraft.options.vignette().set(true);
            minecraft.options.damageTiltStrength().set(1d);
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            minecraft.setScreen(null);
            verifyHurtMatrices();
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (complete || waiting) return;
                try { tick(); } catch (Throwable failure) { finish(failure); }
            });
        } catch (Throwable failure) { finish(failure); }
    }

    private void tick() throws Exception {
        require(System.nanoTime() - started < 50_000_000_000L, "Native effects QA timed out");
        if (minecraft.getOverlay() != null || ++ticks < 20) return;
        switch (phase) {
            case 0 -> capture("effects-baseline", image -> {
                cornerBrightness = corner(image);
                baselineGround = crop(image, .05f, .52f, .55f, .85f);
                baselineHand = crop(image, .70f, .50f, .98f, .93f);
                VignetteConfig.strength = 100;
            });
            case 1 -> capture("effects-vignette-100", image -> {
                double dark = corner(image);
                report.put("vignetteCornerBrightness", Map.of("zero", cornerBrightness, "hundred", dark));
                require(dark < cornerBrightness * .8, "Static vignette strength did not darken the native corner image");
                VignetteConfig.strength = 0;
                LightLevelConfig.setRadius(8); LightLevelConfig.setLightSource(0); LightLevelConfig.setOutlines(true); LightLevelConfig.setEnabled(true);
            });
            case 2 -> capture("effects-light-level", image -> {
                try {
                    Field field = LightLevelOverlay.class.getDeclaredField("cells"); field.setAccessible(true);
                    int cells = ((List<?>) field.get(null)).size();
                    int pixels = differences(baselineGround, crop(image, .05f, .52f, .55f, .85f));
                    report.put("lightLevel", Map.of("nativeCells", cells, "groundChangedPixels", pixels));
                    require(cells > 0 && pixels >= 100, "Light Level did not produce native world labels/outlines");
                } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
                LightLevelConfig.setEnabled(false);
                NoRenderConfig.setEnabled(true); NoRenderConfig.setFirstPersonHand(true);
                QaTickScheduler.execute(() -> { NoRender.tick(); NativeHandProbe.start(); });
            });
            case 3 -> capture("effects-no-render-hand", image -> {
                hiddenHand = crop(image, .70f, .50f, .98f, .93f);
                int pixels = differences(baselineHand, hiddenHand);
                report.put("noRenderHandChangedPixels", pixels);
                var hiddenCalls = NativeHandProbe.finish();
                report.put("hiddenHandNativeCalls", hiddenCalls);
                require(NoRender.effect(NoRenderPolicy.Effect.FIRST_PERSON_HAND) && hiddenCalls.get("completed") == 0, "NoRender did not cancel the actual native hand renderer");
                require(pixels >= 100, "NoRender hand option did not change the first-person native hand image");
                NoRenderConfig.setEnabled(false);
                QaTickScheduler.execute(() -> { NoRender.tick(); NativeHandProbe.start(); });
            });
            case 4 -> capture("effects-no-render-master-off", image -> {
                int pixels = differences(hiddenHand, crop(image, .70f, .50f, .98f, .93f));
                report.put("noRenderMasterOffChangedPixels", pixels);
                var restoredCalls = NativeHandProbe.finish();
                report.put("masterOffHandNativeCalls", restoredCalls);
                require(!NoRender.effect(NoRenderPolicy.Effect.FIRST_PERSON_HAND) && restoredCalls.get("completed") > 0, "Disabling the NoRender master did not restore actual native hand calls");
                require(pixels >= 100, "NoRender master switch did not restore native hands");
            });
            case 5 -> finish(null);
            default -> throw new IllegalStateException("Unknown native effects phase " + phase);
        }
    }

    private void verifyHurtMatrices() {
        var player = minecraft.player;
        int time = player.hurtTime, duration = player.hurtDuration;
        float direction = player.getHurtDir();
        try {
            player.animateHurt(45); player.hurtTime = player.hurtDuration = 10;
            hurt.enabled = true; hurt.type = "YAW_BASED"; hurt.multiplier = 1;
            Matrix4f full = hurtMatrix();
            hurt.multiplier = 0;
            Matrix4f zero = hurtMatrix();
            require(zero.equals(new Matrix4f(), .00001f), "Zero HurtCam multiplier did not produce a native identity pose");
            hurt.multiplier = .5;
            Matrix4f half = hurtMatrix();
            double fullAngle = angle(full), halfAngle = angle(half);
            require(fullAngle > .01 && Math.abs(halfAngle - fullAngle * .5) < .001, "HurtCam multiplier did not halve the native camera rotation");
            hurt.multiplier = 1; hurt.type = "OLD";
            Matrix4f old = hurtMatrix();
            Quaternionf rotation = old.getUnnormalizedRotation(new Quaternionf()).normalize();
            require(Math.abs(rotation.x) < .00001 && Math.abs(rotation.y) < .00001 && Math.abs(rotation.z) > .001, "OLD HurtCam did not remove native Y-axis conjugation");
            report.put("hurtCamera", Map.of("fullAngleRadians", fullAngle, "halfAngleRadians", halfAngle,
                    "zeroIdentity", true, "oldRotationQuaternion", List.of(rotation.x, rotation.y, rotation.z, rotation.w)));
        } finally {
            player.animateHurt(direction); player.hurtTime = time; player.hurtDuration = duration;
            hurt.enabled = hurtEnabled; hurt.multiplier = hurtMultiplier; hurt.type = hurtType;
        }
    }

    private Matrix4f hurtMatrix() {
        var pose = new PoseStack();
        ((GameRendererQaAccess) minecraft.gameRenderer).arcadium$hurtPose(pose, .5f);
        return new Matrix4f(pose.last().pose());
    }

    private static double angle(Matrix4f matrix) {
        Quaternionf value = matrix.getUnnormalizedRotation(new Quaternionf()).normalize();
        return 2 * Math.acos(Math.min(1, Math.abs(value.w)));
    }

    private void capture(String name, java.util.function.Consumer<NativeImage> validate) {
        waiting = true;
        Screenshot.takeScreenshot(minecraft.getMainRenderTarget(), image -> {
            try (image) {
                image.writeToFile(output.resolve(name + ".png")); images.add(name + ".png"); validate.accept(image);
                QaTickScheduler.execute(() -> { waiting = false; phase++; ticks = 0; });
            } catch (Throwable failure) { QaTickScheduler.execute(() -> finish(failure)); }
        });
    }

    private void finish(Throwable failure) {
        if (complete) return;
        complete = true;
        try {
            for (var entry : filters.entrySet()) entry.getKey().setBoolean(null, entry.getValue());
            NativeHandProbe.finish();
            NoRender.tick();
            LightLevelConfig.setEnabled(light); LightLevelConfig.setRadius(radius); LightLevelConfig.setLightSource(source); LightLevelConfig.setOutlines(outlines);
            VignetteConfig.enabled = vignette; VignetteConfig.type = vignetteType; VignetteConfig.strength = strength;
            VignetteConfig.Health.enabled = statuses[0]; VignetteConfig.Hunger.enabled = statuses[1]; VignetteConfig.Air.enabled = statuses[2];
            hurt.enabled = hurtEnabled; hurt.multiplier = hurtMultiplier; hurt.type = hurtType;
            minecraft.options.vignette().set(nativeVignette); minecraft.options.damageTiltStrength().set(damageTilt);
            minecraft.options.setCameraType(camera); minecraft.setScreen(screen);
            report.put("images", images); report.put("passed", failure == null); report.put("restored", true);
            if (failure != null) report.put("error", failure.toString());
            Files.writeString(output.resolve("runtime-effects.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
            if (failure == null) completion.complete(report); else completion.completeExceptionally(failure);
        } catch (Throwable restoreFailure) { completion.completeExceptionally(restoreFailure); }
    }

    private static int[] crop(NativeImage image, float x1, float y1, float x2, float y2) {
        int left = (int) (image.getWidth() * x1), top = (int) (image.getHeight() * y1);
        int width = (int) (image.getWidth() * x2) - left, height = (int) (image.getHeight() * y2) - top;
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) pixels[y * width + x] = image.getPixel(left + x, top + y);
        return pixels;
    }

    private static int differences(int[] first, int[] second) {
        require(first.length == second.length, "Effects QA framebuffer changed during comparison");
        int changed = 0;
        for (int index = 0; index < first.length; index++) if (first[index] != second[index]) changed++;
        return changed;
    }

    private static double corner(NativeImage image) {
        int[] pixels = crop(image, .91f, .01f, .99f, .09f);
        long sum = 0;
        for (int pixel : pixels) sum += (pixel >>> 16 & 255) + (pixel >>> 8 & 255) + (pixel & 255);
        return sum / (pixels.length * 3d);
    }

    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
