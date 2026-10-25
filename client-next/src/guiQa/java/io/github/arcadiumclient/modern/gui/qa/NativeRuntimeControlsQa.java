// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.gui.qa;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.arcadiumclient.modern.client.features.NoRender;
import io.github.arcadiumclient.modern.client.features.NoRenderConfig;
import io.github.arcadiumclient.modern.client.features.NickHiderConfig;
import io.github.arcadiumclient.modern.gui.qa.mixin.KeyboardQaAccess;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.particles.ParticleTypes;
import org.lwjgl.glfw.GLFW;

final class NativeRuntimeControlsQa {
    private final Minecraft mc;
    private final Path output;
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final Map<Field, Boolean> filters = new LinkedHashMap<>();
    private final CompletableFuture<Map<String, Object>> completion = new CompletableFuture<>();
    private final CameraType previousCamera;
    private final boolean nicknameEnabled = NickHiderConfig.getEnabled();
    private final String nickname = NickHiderConfig.getNickname();
    private final long started = System.nanoTime();
    private KeyMapping zoomMapping;
    private String zoomKey;
    private Class<?> zoomType;
    private Object zoom;
    private int phase, ticks;
    private boolean complete, waiting;
    private float baselineFov;
    private int[] visibleHand, hiddenHand;

    static CompletableFuture<Map<String, Object>> run(Minecraft mc, Path output) {
        var qa = new NativeRuntimeControlsQa(mc, output);
        qa.start();
        return qa.completion;
    }
    private NativeRuntimeControlsQa(Minecraft mc, Path output) {
        this.mc = mc; this.output = output; previousCamera = mc.options.getCameraType();
    }
    private void start() {
        try {
            mc.gui.setScreen(null); mc.options.setCameraType(CameraType.FIRST_PERSON);
            NickHiderConfig.setEnabled(true); NickHiderConfig.setNickname("Mask");
            for (Field field : NoRenderConfig.class.getDeclaredFields())
                if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers()) && field.getType() == boolean.class) {
                    field.setAccessible(true); filters.put(field, field.getBoolean(null)); field.setBoolean(null, false);
                }
            NoRender.tick(); NativeRuntimeProbe.beginHand();
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (complete || waiting) return;
                try { tick(); } catch (Throwable failure) { finish(failure); }
            });
        } catch (Throwable failure) { finish(failure); }
    }
    private void tick() throws Exception {
        require(System.nanoTime() - started < 60_000_000_000L, "Native runtime controls QA timed out in phase " + phase);
        if (mc.gui.overlay() != null || ++ticks < 20) return;
        switch (phase) {
            case 0 -> capture("runtime-hands-visible", image -> {
                visibleHand = hand(image);
                var calls = NativeRuntimeProbe.hand(); report.put("visibleHandCalls", calls);
                require(calls.get("attempts") > 0 && calls.get("completed") > 0, "The native hand renderer did not complete before NoRender");
                NoRenderConfig.setEnabled(true); NoRenderConfig.setFirstPersonHand(true); NoRenderConfig.setAllParticles(true);
                NoRender.tick(); NativeRuntimeProbe.beginHand();
                var particle = mc.particleEngine.createParticle(ParticleTypes.SMOKE, mc.player.getX(), mc.player.getY() + 2, mc.player.getZ(), 0, 0, 0);
                require(particle == null, "NoRender did not cancel the actual native particle factory");
                report.put("nativeParticleFactoryCancelled", true);
            });
            case 1 -> capture("runtime-hands-hidden", image -> {
                hiddenHand = hand(image);
                int changed = differences(visibleHand, hiddenHand); var calls = NativeRuntimeProbe.hand();
                report.put("hiddenHandCalls", calls); report.put("hiddenHandChangedPixels", changed);
                require(calls.get("attempts") > 0 && calls.get("completed") == 0, "NoRender did not cancel the native hand submission stage");
                require(changed > 100, "NoRender did not remove the native hand image");
                NoRenderConfig.setEnabled(false); NoRender.tick(); NativeRuntimeProbe.beginHand();
                var particle = mc.particleEngine.createParticle(ParticleTypes.SMOKE, mc.player.getX(), mc.player.getY() + 2, mc.player.getZ(), 0, 0, 0);
                require(particle != null, "Turning off NoRender did not restore the native particle factory");
                particle.setLifetime(1); report.put("nativeParticleFactoryRestored", true);
            });
            case 2 -> capture("runtime-hands-restored", image -> {
                var calls = NativeRuntimeProbe.hand(); int changed = differences(hiddenHand, hand(image));
                report.put("restoredHandCalls", calls); report.put("restoredHandChangedPixels", changed);
                require(calls.get("completed") > 0 && changed > 100, "NoRender master switch did not restore native hand submissions and image");
            });
            case 3 -> {
                verifyNicknameLayout();
                zoomType = Class.forName("dev.isxander.zoomify.Zoomify"); zoom = zoomType.getField("INSTANCE").get(null);
                var field = zoomType.getDeclaredField("zoomKey"); field.setAccessible(true); zoomMapping = (KeyMapping) field.get(null);
                zoomKey = zoomMapping.saveString(); zoomMapping.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_C)); KeyMapping.resetMapping();
                baselineFov = mc.gameRenderer.mainCamera().getFov(); report.put("nativeInput", NativeInputQa.pulse(mc));
                key(GLFW.GLFW_PRESS); next();
            }
            case 4 -> {
                double divisor = ((Number) zoomType.getMethod("getZoomDivisor", float.class).invoke(null, .5f)).doubleValue();
                float actualFov = mc.gameRenderer.mainCamera().getFov();
                report.put("zoomPressed", Map.of("nativeKey", "C", "zooming", zoomType.getMethod("getZooming").invoke(zoom), "divisor", divisor, "cameraFov", actualFov, "baselineCameraFov", baselineFov));
                require((boolean) zoomType.getMethod("getZooming").invoke(zoom) && divisor > 1.1, "Actual native C input did not activate Zoomify");
                require(actualFov < baselineFov * .9f, "Zoom did not change the actual native camera FOV");
                key(GLFW.GLFW_RELEASE); next();
            }
            case 5 -> {
                float actualFov = mc.gameRenderer.mainCamera().getFov();
                require(!(boolean) zoomType.getMethod("getZooming").invoke(zoom) && Math.abs(actualFov - baselineFov) < 1,
                    "Releasing actual native C input did not restore camera FOV");
                report.put("zoomReleased", Map.of("zooming", false, "cameraFov", actualFov)); finish(null);
            }
            default -> throw new IllegalStateException("Unknown runtime QA phase " + phase);
        }
    }
    private void capture(String name, java.util.function.Consumer<NativeImage> validate) {
        waiting = true;
        Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
            try (image) {
                image.writeToFile(output.resolve(name + ".png")); validate.accept(image);
                QaTickScheduler.execute(() -> { waiting = false; next(); });
            } catch (Throwable failure) { QaTickScheduler.execute(() -> finish(failure)); }
        });
    }
    private static int[] hand(NativeImage image) {
        int left = image.getWidth() * 70 / 100, right = image.getWidth() * 98 / 100;
        int top = image.getHeight() / 2, bottom = image.getHeight() * 93 / 100;
        int[] pixels = new int[(right - left) * (bottom - top)];
        for (int y = top; y < bottom; y++) for (int x = left; x < right; x++) pixels[(y - top) * (right - left) + x - left] = image.getPixel(x, y);
        return pixels;
    }
    private static int differences(int[] before, int[] after) {
        require(before.length == after.length, "Native framebuffer dimensions changed during runtime QA");
        int changed = 0; for (int i = 0; i < before.length; i++) if (before[i] != after[i]) changed++; return changed;
    }
    private void key(int action) {
        ((KeyboardQaAccess) mc.keyboardHandler).arcadium$nativeKey(mc.getWindow().handle(), action, new KeyEvent(GLFW.GLFW_KEY_C, 0, 0));
    }
    private void verifyNicknameLayout() {
        var text = net.minecraft.network.chat.Component.literal("[VIP] ")
            .append(net.minecraft.network.chat.Component.literal(mc.getUser().getName()).withStyle(net.minecraft.ChatFormatting.BOLD))
            .append(net.minecraft.network.chat.Component.literal(" [GUEST]"));
        int expected = mc.font.width(text.getVisualOrderText());
        var camera = new net.minecraft.client.renderer.state.level.CameraRenderState(); camera.orientation = new org.joml.Quaternionf();
        var collector = new net.minecraft.client.renderer.SubmitNodeCollection();
        collector.submitNameTag(new com.mojang.blaze3d.vertex.PoseStack(), net.minecraft.world.phys.Vec3.ZERO, 0, text, true, 0xF000F0, camera);
        var positions = new java.util.ArrayList<Float>();
        for (var phase : collector.allPhases()) phase.sortInto((submit, sorted) -> {
            if (submit instanceof net.minecraft.client.renderer.feature.NameTagFeatureRenderer.Submit name) positions.add(name.x());
        });
        require(!positions.isEmpty() && positions.stream().allMatch(x -> Math.abs(x + expected / 2f) < .001f),
            "Native name-tag centering did not match the actual masked glyph width: " + positions + " / " + expected);
        report.put("nativeNicknameLayout", Map.of("renderedGlyphWidth", expected, "nativeSubmitX", positions, "boldStylePreserved", true));
    }
    private void next() { phase++; ticks = 0; }
    private void finish(Throwable failure) {
        if (complete) return;
        complete = true;
        try {
            if (zoomMapping != null) { key(GLFW.GLFW_RELEASE); zoomMapping.setKey(InputConstants.getKey(zoomKey)); KeyMapping.resetMapping(); }
            for (var entry : filters.entrySet()) entry.getKey().setBoolean(null, entry.getValue());
            NickHiderConfig.setEnabled(nicknameEnabled); NickHiderConfig.setNickname(nickname);
            NoRender.tick(); mc.options.setCameraType(previousCamera);
            report.put("passed", failure == null); report.put("restored", true);
            if (failure != null) report.put("error", failure.toString());
            Files.writeString(output.resolve("runtime-controls.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
            if (failure == null) completion.complete(report); else completion.completeExceptionally(failure);
        } catch (Throwable restoring) { completion.completeExceptionally(restoring); }
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
