// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.gui.qa;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.arcadiumclient.modern.client.skins.SkinChanger;
import io.github.arcadiumclient.modern.client.skins.SkinLibrary;
import io.github.arcadiumclient.modern.gui.SkinChangerScreen;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

final class ModernSkinQa {
    private final Minecraft minecraft;
    private final Path output;
    private final SkinLibrary library = SkinChanger.INSTANCE.getLibrary();
    private final CompletableFuture<Map<String, Object>> completion = new CompletableFuture<>();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final List<String> images = new ArrayList<>();
    private final long started = System.nanoTime();
    private final Screen previousScreen;
    private SkinLibrary.Entry previousSelection, entry;
    private byte[] previousPng;
    private SkinChangerScreen editor;
    private Fixture fixture;
    private int[] baseline;
    private String baselineTexture, baselineModel;
    private int baselineMagenta;
    private int phase, ticks;
    private boolean waiting, complete, snapshotCaptured;

    static CompletableFuture<Map<String, Object>> run(Minecraft minecraft, Path output) {
        var qa = new ModernSkinQa(minecraft, output);
        qa.start();
        return qa.completion;
    }

    private ModernSkinQa(Minecraft minecraft, Path output) {
        this.minecraft = minecraft; this.output = output;
        previousScreen = minecraft.screen;
    }

    private void start() {
        try {
            require(minecraft.player != null && minecraft.hasSingleplayerServer() && SkinChanger.isOffline(), "Skin QA requires the synthetic offline singleplayer account");
            Path config = FabricLoader.getInstance().getConfigDir().toAbsolutePath().normalize();
            Path isolation = output.getParent().getParent().toAbsolutePath().normalize();
            require(isolation.getFileName().toString().equals("ArcadiumModern050Qa") && config.startsWith(isolation), "Skin QA must use the isolated ArcadiumModern050Qa profile");
            previousSelection = library.localSelection(minecraft.getUser().getProfileId());
            if (previousSelection != null) previousPng = library.png(previousSelection.id);
            snapshotCaptured = true;
            SkinChanger.INSTANCE.clearSkinPreview();
            fixture = new Fixture();
            minecraft.setScreen(fixture);
            report.put("account", "Synthetic offline account; no launcher account service or credentials are changed");
            report.put("nativePipeline", "SkinChangerScreen save/apply/reset/delete handlers, persistent SkinLibrary, AbstractClientPlayer.getSkin, TextureManager, native InventoryScreen entity renderer");
            report.put("onlineCoverage", "Microsoft/session authentication, online uploads and owned cape changes are not exercised");
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (complete || waiting) return;
                try { tick(); } catch (Throwable failure) { finish(failure); }
            });
        } catch (Throwable failure) { finish(failure); }
    }

    private void tick() throws Exception {
        require(System.nanoTime() - started < 65_000_000_000L, "Native skin QA timed out in phase " + phase);
        require(minecraft.player != null, "Native skin QA lost its synthetic player");
        if (minecraft.getOverlay() != null || ++ticks < 15) return;
        switch (phase) {
            case 0 -> capture("skin-native-default", image -> {
                baseline = entityPixels(image);
                baselineMagenta = magenta(baseline);
                baselineTexture = minecraft.player.getSkin().body().texturePath().toString();
                baselineModel = minecraft.player.getSkin().model().getSerializedName();
                report.put("nativeBaseline", Map.of("texture", baselineTexture, "model", baselineModel, "magentaPixels", baselineMagenta));
            }, () -> {
                Path source = output.resolve("generated-skin.png");
                try (var generated = new NativeImage(64, 64, false)) {
                    for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) generated.setPixel(x, y, 0xFFE020E0);
                    generated.writeToFile(source);
                }
                entry = library.add(source, "QA generated skin", "classic");
                require(entry.height == 64 && library.png(entry.id).length > 0, "Generated skin was not imported through the actual library");
                editor = new SkinChangerScreen(fixture);
                minecraft.setScreen(editor);
            });
            case 1 -> {
                if (!editorReady()) return;
                state("selected", entry.id); state("name", "QA saved slim skin"); state("variant", "slim");
                invoke("saveDetails"); next();
            }
            case 2 -> {
                if (!editorReady()) return;
                var saved = new SkinLibrary(FabricLoader.getInstance().getConfigDir().resolve("arcadium/skins")).list().stream().filter(value -> value.id.equals(entry.id)).findFirst().orElseThrow();
                require(saved.name.equals("QA saved slim skin") && saved.variant.equals("slim"), "Skin editor did not save name/model to the real index");
                report.put("savedLibrary", Map.of("name", saved.name, "variant", saved.variant, "height", saved.height));
                capture("skin-library-saved", image -> {
                    int magenta = magenta(image);
                    report.put("libraryPreviewMagentaPixels", magenta);
                    require(magenta >= 300, "The imported skin was not visibly rendered in the actual library preview");
                }, () -> invoke("applySkin"));
            }
            case 3 -> {
                if (!editorReady()) return;
                var id = minecraft.getUser().getProfileId();
                var selection = library.localSelection(id);
                var texture = SkinChanger.textureFor(id);
                require(selection != null && selection.id.equals(entry.id) && selection.variant.equals("slim"), "Offline skin choice was not persisted per account");
                require(texture != null && minecraft.player.getSkin().body().texturePath().equals(texture), "The actual native player skin hook did not use the selected local skin");
                require(SkinChanger.modelFor(id).equals("slim") && minecraft.getTextureManager().getTexture(texture).getTextureView() != null, "Local skin model or GPU texture was not initialized");
                report.put("offlineApply", Map.of("localSelection", true, "nativePlayerSkin", texture.toString(), "nativeModel", minecraft.player.getSkin().model().getSerializedName(), "selectedModel", "slim", "gpuTextureView", true));
                minecraft.setScreen(fixture); next();
            }
            case 4 -> capture("skin-offline-applied-native", image -> {
                int[] nativePixels = entityPixels(image);
                int changed = differences(baseline, nativePixels), magenta = magenta(nativePixels);
                report.put("nativeSkinPixels", Map.of("changedFromDefault", changed, "magenta", magenta));
                require(changed >= 300 && magenta >= 100, "The local skin did not change the actual native player-rendered image");
            }, () -> { minecraft.setScreen(editor); invoke("resetLocalSkin"); });
            case 5 -> {
                if (!editorReady()) return;
                var id = minecraft.getUser().getProfileId();
                require(library.localSelection(id) == null && SkinChanger.textureFor(id) == null, "Reset local skin did not clear the persisted selection and native override");
                require(minecraft.player.getSkin().body().texturePath().toString().equals(baselineTexture) && minecraft.player.getSkin().model().getSerializedName().equals(baselineModel), "Reset did not restore the initial native skin texture/model identity");
                report.put("offlineReset", true);
                minecraft.setScreen(fixture); next();
            }
            case 6 -> capture("skin-native-reset", image -> {
                int[] reset = entityPixels(image);
                int magenta = magenta(reset), changed = differences(baseline, reset);
                int foreground = 0;
                for (int value : baseline) if (value != baseline[0]) foreground++;
                report.put("resetImage", Map.of("entityViewportOnly", true, "initialTextureRestored", true, "initialModelRestored", true,
                        "baselineMagentaPixels", baselineMagenta, "resetMagentaPixels", magenta, "changedFromBaseline", changed, "baselineForegroundPixels", foreground));
                require(magenta <= baselineMagenta + Math.max(20, baselineMagenta / 10) && changed <= Math.max(300, foreground / 10), "Reset did not restore the native baseline player image within idle pose tolerance");
            }, () -> { minecraft.setScreen(editor); invoke("deleteSkin"); });
            case 7 -> {
                if (!editorReady()) return;
                require(library.list().stream().noneMatch(value -> value.id.equals(entry.id)), "Skin editor did not delete the imported library entry");
                report.put("libraryDelete", true); finish(null);
            }
            default -> throw new IllegalStateException("Unknown skin QA phase " + phase);
        }
    }

    private boolean editorReady() throws Exception {
        Object error = state("error");
        require(error == null, "Skin editor reported: " + error);
        return state("busy") == null;
    }

    @SuppressWarnings("unchecked") private void state(String name, Object value) throws Exception {
        var field = SkinChangerScreen.class.getDeclaredField(name + "$delegate"); field.setAccessible(true);
        ((androidx.compose.runtime.MutableState<Object>) field.get(editor)).setValue(value);
    }
    private Object state(String name) throws Exception {
        var field = SkinChangerScreen.class.getDeclaredField(name + "$delegate"); field.setAccessible(true);
        return ((androidx.compose.runtime.State<?>) field.get(editor)).getValue();
    }
    private void invoke(String name) throws Exception {
        Method method = SkinChangerScreen.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(editor);
    }

    @FunctionalInterface private interface Next { void run() throws Exception; }
    private void capture(String name, java.util.function.Consumer<NativeImage> validate, Next action) {
        waiting = true;
        Screenshot.takeScreenshot(minecraft.getMainRenderTarget(), image -> {
            try (image) {
                image.writeToFile(output.resolve(name + ".png")); images.add(name + ".png"); validate.accept(image);
                QaTickScheduler.execute(() -> {
                    try { action.run(); waiting = false; next(); } catch (Throwable failure) { finish(failure); }
                });
            } catch (Throwable failure) { QaTickScheduler.execute(() -> finish(failure)); }
        });
    }
    private void next() { phase++; ticks = 0; }

    private void finish(Throwable failure) {
        if (complete) return;
        complete = true;
        try {
            if (snapshotCaptured) {
                var account = minecraft.getUser().getProfileId();
                if (entry != null && library.list().stream().anyMatch(value -> value.id.equals(entry.id))) library.delete(entry.id);
                library.selectLocal(account, previousSelection == null ? null : previousSelection.id, previousSelection == null ? null : previousSelection.variant);
                if (previousSelection == null) SkinChanger.INSTANCE.clearSkinPreview();
                else SkinChanger.INSTANCE.refresh(account, previousPng, previousSelection.variant);
            }
            minecraft.setScreen(previousScreen);
            report.put("images", images); report.put("passed", failure == null); report.put("restored", true);
            if (failure != null) report.put("error", failure.toString());
            Files.writeString(output.resolve("skins.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
            if (failure == null) completion.complete(report); else completion.completeExceptionally(failure);
        } catch (Throwable restoreFailure) { completion.completeExceptionally(restoreFailure); }
    }

    private static int magenta(NativeImage image) {
        return magenta(pixels(image));
    }
    private static int magenta(int[] pixels) {
        int count = 0;
        for (int pixel : pixels) {
            int r = pixel >>> 16 & 255, g = pixel >>> 8 & 255, b = pixel & 255;
            if (Math.min(r, b) > 70 && Math.min(r, b) > g * 2) count++;
        }
        return count;
    }
    private int[] entityPixels(NativeImage image) {
        double scaleX = image.getWidth() / (double) fixture.width, scaleY = image.getHeight() / (double) fixture.height;
        int left = (int) ((fixture.width / 2 - 80) * scaleX), right = (int) ((fixture.width / 2 + 80) * scaleX);
        int top = (int) (20 * scaleY), bottom = (int) ((fixture.height - 15) * scaleY);
        var pixels = new int[(right - left) * (bottom - top)];
        for (int y = top; y < bottom; y++) for (int x = left; x < right; x++) pixels[(y - top) * (right - left) + x - left] = image.getPixel(x, y);
        return pixels;
    }
    private static int[] pixels(NativeImage image) {
        var pixels = new int[image.getWidth() * image.getHeight()];
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) pixels[y * image.getWidth() + x] = image.getPixel(x, y);
        return pixels;
    }
    private static int differences(int[] first, int[] second) {
        require(first.length == second.length, "Skin QA framebuffer dimensions changed");
        int count = 0;
        for (int index = 0; index < first.length; index++) if (first[index] != second[index]) count++;
        return count;
    }

    private final class Fixture extends Screen {
        Fixture() { super(Component.literal("Offline skin QA")); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            graphics.fill(0, 0, width, height, 0xFF101820);
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, width / 2 - 80, 20, width / 2 + 80, height - 15, 65, .0625f,
                    width / 2f, height / 2f, minecraft.player);
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
