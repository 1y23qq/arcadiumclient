// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.gui.qa;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.arcadiumclient.modern.client.features.CustomCrosshair;
import io.github.arcadiumclient.modern.client.features.LegacyCrosshairTweaksConfig;
import io.github.arcadiumclient.modern.client.features.LightLevelConfig;
import io.github.arcadiumclient.modern.client.features.NoRender;
import io.github.arcadiumclient.modern.client.features.NoRenderConfig;
import io.github.arcadiumclient.modern.client.features.VignetteConfig;
import io.github.arcadiumclient.modern.client.profile.LegacySmoothSkyOptions;
import io.github.arcadiumclient.modern.gui.qa.mixin.GuiCrosshairQaAccess;
import io.github.arcadiumclient.modern.gui.qa.mixin.LevelSkyQaAccess;
import io.github.arcadiumclient.modern.gui.qa.mixin.SkyRendererQaAccess;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.SkyRenderState;
import net.minecraft.network.chat.Component;
import org.polyfrost.oneconfig.api.config.v1.ConfigManager;

final class ModernLegacyVisualsQa {
    private final Minecraft mc;
    private final Path output;
    private final CompletableFuture<Map<String, Object>> result = new CompletableFuture<>();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final Map<Field, Object> legacy = new LinkedHashMap<>();
    private final Screen screen;
    private final CameraType camera;
    private final boolean hideGui, canvas, vignette, light, noRender;
    private final CloudStatus clouds;
    private final float yaw, pitch;
    private final LegacySmoothSkyOptions.Selection sky;
    private final long started = System.nanoTime();
    private int stage, ticks;
    private boolean waiting, done;
    private int[] zero, normalFull, invertedFull;
    private Map<String, Integer> sunriseVisible;

    static CompletableFuture<Map<String, Object>> run(Minecraft mc, Path output) {
        var qa = new ModernLegacyVisualsQa(mc, output); qa.start(); return qa.result;
    }
    private ModernLegacyVisualsQa(Minecraft mc, Path output) {
        this.mc = mc; this.output = output; screen = mc.screen; camera = mc.options.getCameraType();
        hideGui = mc.options.hideGui; clouds = mc.options.cloudStatus().get(); canvas = CustomCrosshair.INSTANCE.enabled();
        vignette = VignetteConfig.enabled; light = LightLevelConfig.getEnabled(); noRender = NoRenderConfig.getEnabled();
        yaw = mc.player.getYRot(); pitch = mc.player.getXRot();
        sky = new LegacySmoothSkyOptions.Selection(LegacySmoothSkyOptions.removeVoidBox(), LegacySmoothSkyOptions.removeSunriseColor());
    }
    private void start() {
        try {
            for (Field field : LegacyCrosshairTweaksConfig.class.getDeclaredFields()) if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
                legacy.put(field, field.get(null));
            }
            CustomCrosshair.INSTANCE.setEnabled(false);
            LegacyCrosshairTweaksConfig.legacySettings = true; LegacyCrosshairTweaksConfig.disableCrosshair = false;
            LegacyCrosshairTweaksConfig.hideCrosshairInPerspective = false;
            VignetteConfig.enabled = false; LightLevelConfig.setEnabled(false); NoRenderConfig.setEnabled(false); NoRender.tick();
            mc.options.hideGui = true; mc.options.cloudStatus().set(CloudStatus.OFF);
            mc.options.setCameraType(CameraType.FIRST_PERSON); mc.setScreen(new CrosshairScene());
            Class.forName("dev.microcontrollers.smoothskies.config.SmoothConfig").getMethod("configScreen", Screen.class).invoke(null, mc.screen);
            var controls = ConfigManager.active().get("smoothskies");
            require(controls.getProp("arcadiumRemoveVoidBox") != null && controls.getProp("arcadiumRemoveSunriseColor") != null, "SmoothSkies legacy controls are absent from the actual native card");
            verifyDarkDisc();
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (done || waiting) return;
                try { tick(); } catch (Throwable failure) { finish(failure); }
            });
        } catch (Throwable failure) { finish(failure); }
    }
    private void tick() throws Exception {
        require(System.nanoTime() - started < 35_000_000_000L, "Legacy visual QA timed out");
        if (ticks++ == 0) configure();
        if (mc.getOverlay() != null || ticks < 8) return;
        switch (stage) {
            case 0 -> capture("legacy-crosshair-normal-0", image -> zero = crop(image));
            case 1 -> capture("legacy-crosshair-normal-100", image -> { normalFull = crop(image); require(mask(normalFull).length >= 4, "Native legacy crosshair did not render at100opacity"); });
            case 2 -> capture("legacy-crosshair-normal-50", image -> ratio("normalOpacity", crop(image), normalFull));
            case 3 -> capture("legacy-crosshair-inverted-100", image -> { invertedFull = crop(image); require(mask(invertedFull).length >= 4, "Native inverted crosshair did not render"); });
            case 4 -> capture("legacy-crosshair-inverted-50", image -> ratio("invertedOpacity", crop(image), invertedFull));
            case 5 -> capture("legacy-crosshair-inverted-0", image -> require(changes(crop(image), zero, 3) == 0, "Inverted zero-opacity crosshair still changed native pixels"));
            case 6 -> capture("legacy-crosshair-back", image -> { int count = changes(crop(image), zero, 18); report.put("backThirdPersonPixels", count); require(count >= 4, "Native legacy crosshair is absent in back third person"); });
            case 7 -> capture("legacy-crosshair-front", image -> { int count = changes(crop(image), zero, 18); report.put("frontThirdPersonPixels", count); require(count >= 4, "Native legacy crosshair is absent in front third person"); });
            case 8 -> capture("legacy-sunrise-visible", image -> {
                sunriseVisible = NativeLegacySkyProbe.finish();
                report.put("sunriseNativeVisible", sunriseVisible);
                require(sunriseVisible.get("attempted") > 0 && sunriseVisible.get("gpuDraws") > 0 && sunriseVisible.get("completed") > 0, "Native sunrise renderer did not reach the real GPUdrawcommand");
            });
            case 9 -> capture("legacy-sunrise-hidden", image -> {
                var hidden = NativeLegacySkyProbe.finish();
                report.put("sunriseNativeRender", Map.of("visible", sunriseVisible, "hidden", hidden, "context", "Actual SkyRenderer sky pass, opaque native sunrise mesh"));
                require(hidden.get("attempted") > 0 && hidden.get("hiddenFlagCalls").equals(hidden.get("attempted")) && hidden.get("gpuDraws") == 0 && hidden.get("completed") == 0, "Sunrise switch did not cancel the actual native renderer before GPUsubmission");
            });
            case 10 -> finish(null);
            default -> throw new IllegalStateException("Unexpected legacy visual phase");
        }
    }
    private void configure() {
        int opacity = switch (stage) { case 0, 5 -> 0; case 2, 4 -> 50; default -> 100; };
        LegacyCrosshairTweaksConfig.crosshairOpacityFirstPerson = LegacyCrosshairTweaksConfig.crosshairOpacityThirdPerson = opacity;
        LegacyCrosshairTweaksConfig.removeCrosshairBlendingFirstPerson = LegacyCrosshairTweaksConfig.removeCrosshairBlendingThirdPerson = stage < 3 || stage > 5;
        if (stage == 6) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        if (stage == 7) mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        if (stage >= 8 && stage <= 9) {
            mc.setScreen(null); mc.options.setCameraType(CameraType.FIRST_PERSON); mc.player.setYRot(-90); mc.player.setXRot(-15);
            LegacySmoothSkyOptions.apply(new LegacySmoothSkyOptions.Selection(sky.removeVoidBox(), stage == 9)); NativeLegacySkyProbe.start();
        }
    }
    private void verifyDarkDisc() {
        SkyRenderer renderer = ((LevelSkyQaAccess) mc.levelRenderer).arcadium$sky();
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        try {
            double horizon = mc.level.getLevelData().getHorizonHeight(mc.level);
            mc.player.setPos(x, horizon - 4, z);
            LegacySmoothSkyOptions.apply(new LegacySmoothSkyOptions.Selection(false, sky.removeSunriseColor()));
            boolean visible = ((SkyRendererQaAccess) renderer).arcadium$darkDisc(1, mc.level);
            var before = new SkyRenderState(); renderer.extractRenderState(mc.level, 1, mc.gameRenderer.getMainCamera(), before);
            LegacySmoothSkyOptions.apply(new LegacySmoothSkyOptions.Selection(true, sky.removeSunriseColor()));
            boolean hidden = ((SkyRendererQaAccess) renderer).arcadium$darkDisc(1, mc.level);
            var after = new SkyRenderState(); renderer.extractRenderState(mc.level, 1, mc.gameRenderer.getMainCamera(), after);
            require(visible && before.shouldRenderDarkDisc && !hidden && !after.shouldRenderDarkDisc, "Void switch did not change actual native sky selection/extracted render state");
            report.put("voidNativeState", Map.of("visible", visible, "hidden", hidden, "beforeRenderState", before.shouldRenderDarkDisc, "afterRenderState", after.shouldRenderDarkDisc));
        } finally { mc.player.setPos(x, y, z); LegacySmoothSkyOptions.apply(sky); }
    }
    private void capture(String name, java.util.function.Consumer<NativeImage> verify) {
        waiting = true;
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> {
            try (image) {
                image.writeToFile(output.resolve(name + ".png")); verify.accept(image);
                QaTickScheduler.execute(() -> { stage++; ticks = 0; waiting = false; });
            } catch (Throwable failure) { QaTickScheduler.execute(() -> finish(failure)); }
        });
    }
    private void ratio(String key, int[] half, int[] full) {
        int[] selected = mask(full); double a = 0, b = 0;
        for (int index : selected) { a += brightness(half[index]) - brightness(zero[index]); b += brightness(full[index]) - brightness(zero[index]); }
        double ratio = a / b; report.put(key, Map.of("pixels", selected.length, "halfToFull", ratio));
        require(selected.length >= 4 && ratio > .25 && ratio < .75, "Native50opacity did not produce intermediate pixels for " + key + ": " + ratio);
    }
    private int[] mask(int[] image) { return java.util.stream.IntStream.range(0, image.length).filter(index -> Math.abs(brightness(image[index]) - brightness(zero[index])) > 18).toArray(); }
    private static int changes(int[] a, int[] b, int threshold) { int count = 0; for (int i = 0; i < a.length; i++) if (Math.abs(brightness(a[i]) - brightness(b[i])) > threshold) count++; return count; }
    private static double brightness(int pixel) { return ((pixel >>> 16 & 255) + (pixel >>> 8 & 255) + (pixel & 255)) / 3d; }
    private static int[] crop(NativeImage image) { int radius = 64; int[] pixels = new int[radius * radius * 4]; for (int y = 0; y < radius * 2; y++) for (int x = 0; x < radius * 2; x++) pixels[y * radius * 2 + x] = image.getPixel(image.getWidth() / 2 - radius + x, image.getHeight() / 2 - radius + y); return pixels; }
    private void finish(Throwable failure) {
        if (done) return; done = true;
        try {
            NativeLegacySkyProbe.finish();
            for (var entry : legacy.entrySet()) entry.getKey().set(null, entry.getValue());
            CustomCrosshair.INSTANCE.setEnabled(canvas); VignetteConfig.enabled = vignette; LightLevelConfig.setEnabled(light); NoRenderConfig.setEnabled(noRender); NoRender.tick();
            LegacySmoothSkyOptions.apply(sky); mc.options.hideGui = hideGui; mc.options.cloudStatus().set(clouds);
            mc.options.setCameraType(camera); mc.player.setYRot(yaw); mc.player.setXRot(pitch); mc.setScreen(screen);
            report.put("passed", failure == null); report.put("restored", true);
            if (failure != null) report.put("error", failure.toString());
            Files.writeString(output.resolve("legacy-visuals.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
            if (failure == null) result.complete(report); else result.completeExceptionally(failure);
        } catch (Throwable restoreFailure) { result.completeExceptionally(restoreFailure); }
    }
    private final class CrosshairScene extends Screen {
        private CrosshairScene() { super(Component.literal("Native crosshair QA")); }
        @Override public void renderBackground(GuiGraphics graphics, int x, int y, float delta) {}
        @Override public void render(GuiGraphics graphics, int x, int y, float delta) {
            graphics.fill(0, 0, width, height, 0xFF303030);
            ((GuiCrosshairQaAccess) mc.gui).arcadium$crosshair(graphics, mc.getDeltaTracker());
        }
        @Override public boolean isPauseScreen() { return false; }
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
