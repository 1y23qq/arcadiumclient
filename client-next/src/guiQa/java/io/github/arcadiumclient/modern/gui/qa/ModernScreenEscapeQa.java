// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.gui.qa;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.arcadiumclient.modern.gui.*;
import io.github.arcadiumclient.modern.gui.qa.mixin.KeyboardQaAccess;
import io.github.arcadiumclient.modern.gui.qa.mixin.MouseQaAccess;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUI;
import org.polyfrost.oneconfig.api.ui.v1.page.ModConfigTarget;
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen;
import org.polyfrost.oneconfig.internal.ui.compose.impls.OneConfigUIScreen;

final class ModernScreenEscapeQa {
    @FunctionalInterface private interface Action { void run() throws Exception; }
    private record Step(String name, Action action) { }
    private final Minecraft minecraft;
    private final Path output;
    private final CompletableFuture<Map<String, Object>> completion = new CompletableFuture<>();
    private final List<Step> steps = new ArrayList<>();
    private final List<Map<String, Object>> stages = new ArrayList<>();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final int forward;
    private final long started = System.nanoTime();
    private int phase, ticks;
    private boolean complete, waiting;
    private Vec3 before;
    private ArcadiumPauseScreen pause;
    private ArcadiumVideoSettingsScreen video;

    static CompletableFuture<Map<String, Object>> run(Minecraft minecraft, Path output) {
        var qa = new ModernScreenEscapeQa(minecraft, output);
        qa.start();
        return qa.completion;
    }
    private ModernScreenEscapeQa(Minecraft minecraft, Path output) {
        this.minecraft = minecraft; this.output = output;
        forward = InputConstants.getKey(minecraft.options.keyUp.saveString()).getValue();
    }
    private Screen screen() { return minecraft.gui.screen(); }
    private void add(String name, Action action) { steps.add(new Step(name, action)); }
    private void start() {
        try {
            require(minecraft.level != null && minecraft.player != null, "Screen Escape proof requires a loaded native world");
            minecraft.gui.setScreen(null);
            add("movement-before-settings", this::beginMovement);
            add("native-pause", () -> { endMovement(); escape(); });
            add("pause-options-click", this::pauseOptions);
            add("native-options-video-click", () -> clickNative("options.video"));
            add("video-main", () -> {
                require(screen() instanceof ArcadiumVideoSettingsScreen, "Native video button did not open the Arcadium video screen");
                video = (ArcadiumVideoSettingsScreen) screen();
                alive();
                var model = (VideoSettingsModel) ModernNavigationQa.field(video, "model");
                var pages = model.getOptions().stream().map(VideoSetting::getPage).filter(page -> page != VideoPage.MAIN).distinct().toList();
                var inserted = new ArrayList<Step>();
                for (var page : pages) {
                    inserted.add(new Step(page.getTitle() + "-open-for-escape", () -> click(page.getTitle() + "...")));
                    inserted.add(new Step(page.getTitle() + "-native-escape", () -> { page(page); escape(); }));
                    inserted.add(new Step(page.getTitle() + "-reopen-for-close", () -> { page(VideoPage.MAIN); click(page.getTitle() + "..."); }));
                    inserted.add(new Step(page.getTitle() + "-close-button", () -> { page(page); click("Back"); }));
                }
                steps.addAll(phase + 1, inserted);
                report.put("videoCategories", pages.stream().map(VideoPage::getTitle).toList());
                capture("screen-escape-video-main");
            });
            add("renderer-open", () -> { page(VideoPage.MAIN); click("Renderer settings"); });
            add("renderer-native-escape", () -> { renderer(); capture("screen-escape-native-renderer"); escape(); });
            add("renderer-return", () -> { require(screen() == video, "Renderer Escape did not restore the same video screen"); alive(); click("Renderer settings"); });
            add("renderer-close-button", () -> { renderer(); clickRendererClose(); });
            add("video-main-escape", () -> { require(screen() == video, "Renderer Done did not restore video settings"); page(VideoPage.MAIN); escape(); });
            add("options-escape", () -> { require(screen() instanceof OptionsScreen, "Video Escape did not restore Options"); escape(); });
            add("pause-escape-resume", () -> { require(screen() == pause, "Options Escape did not restore the same pause screen"); alive(); escape(); });
            add("movement-after-escape", this::beginMovement);
            add("pause-for-close-parity", () -> { endMovement(); escape(); });
            add("pause-options-for-close", this::pauseOptions);
            add("native-options-video-for-close", () -> clickNative("options.video"));
            add("video-main-close-button", () -> { require(screen() instanceof ArcadiumVideoSettingsScreen, "Video settings did not reopen"); alive(); click("Back"); });
            add("options-close-parity", () -> { require(screen() instanceof OptionsScreen, "Video close button did not restore Options"); escape(); });
            add("background-open-in-world", () -> { require(screen() == pause, "The pause parent was lost"); minecraft.gui.setScreen(new TitleBackgroundScreen(pause)); });
            add("background-native-escape", () -> { require(screen() instanceof TitleBackgroundScreen, "Background settings did not open"); alive(); capture("screen-escape-background"); escape(); });
            add("background-escape-parent", () -> { require(screen() == pause, "Background Escape did not restore its pause parent"); alive(); escape(); });
            add("movement-after-background", this::beginMovement);
            add("pause-for-norender", () -> { endMovement(); escape(); });
            add("norender-settings-open", () -> {
                require(screen() instanceof ArcadiumPauseScreen, "Native pause did not reopen");
                pause = (ArcadiumPauseScreen) screen();
                OneConfigUI.open(new ModConfigTarget("arcadium-no-render.json", null));
            });
            add("norender-native-escape", () -> { require(screen() instanceof OneConfigUIScreen, "NoRender settings did not open"); alive(); capture("screen-escape-norender"); escape(); });
            add("movement-after-norender", this::beginMovement);
            add("pause-for-background-close", () -> { endMovement(); escape(); });
            add("background-open-for-close", () -> { require(screen() instanceof ArcadiumPauseScreen, "Native pause did not reopen"); pause = (ArcadiumPauseScreen) screen(); minecraft.gui.setScreen(new TitleBackgroundScreen(pause)); });
            add("background-close-button", () -> { require(screen() instanceof TitleBackgroundScreen, "Background settings did not reopen"); alive(); click("Back"); });
            add("background-close-parent", () -> { require(screen() == pause, "Background close button did not restore its pause parent"); alive(); escape(); });
            add("native-attack-resume", () -> {
                resume();
                var mouse = (MouseQaAccess) minecraft.mouseHandler;
                long window = minecraft.getWindow().handle();
                var event = new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0);
                mouse.arcadium$nativeButton(window, event, InputConstants.PRESS);
                require(minecraft.options.keyAttack.isDown(), "Native attack click remained blocked after closing settings");
                mouse.arcadium$nativeButton(window, event, InputConstants.RELEASE);
                require(!minecraft.options.keyAttack.isDown(), "Native attack release remained blocked");
                report.put("nativeAttackPressAndRelease", true);
            });
            report.put("stages", stages);
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (complete || waiting) return;
                try { tick(); } catch (Throwable failure) { finish(failure); }
            });
        } catch (Throwable failure) { finish(failure); }
    }
    private void tick() throws Exception {
        require(System.nanoTime() - started < 180_000_000_000L, "Native settings Escape proof timed out at " + phase);
        if (++ticks < 12 || minecraft.gui.overlay() != null) return;
        if (phase == steps.size()) { finish(null); return; }
        if (steps.get(phase).name.equals("movement-after-norender") && screen() instanceof OneConfigUIScreen && ticks < 100) return;
        var step = steps.get(phase);
        step.action.run();
        stages.add(Map.of("name", step.name, "screen", screen() == null ? "none" : screen().getClass().getName(), "mouseGrabbed", minecraft.mouseHandler.isMouseGrabbed()));
        phase++; ticks = 0;
    }
    private void renderer() { require(screen() != null && screen().getClass().getName().startsWith("net.caffeinemc.mods.sodium"), "Renderer settings did not open the genuine Sodium UI: " + screen()); }
    private void page(VideoPage page) throws Exception {
        require(screen() == video, "Video page lost its owning screen");
        Object current = ((androidx.compose.runtime.State<?>) ModernNavigationQa.field(video, "page$delegate")).getValue();
        require(current == page, "Video close/escape did not restore the expected page: " + current + " vs " + page);
        alive();
    }
    private void pauseOptions() throws Exception {
        require(screen() instanceof ArcadiumPauseScreen, "Native Escape did not open the Arcadium pause menu");
        pause = (ArcadiumPauseScreen) screen(); alive(); click("Options");
    }
    private void alive() throws Exception {
        require(screen() instanceof ComposeScreen && ModernNavigationQa.field(screen(), "sceneOrNull") != null
                && Boolean.TRUE.equals(ModernNavigationQa.field(screen(), "showing"))
                && !Boolean.TRUE.equals(ModernNavigationQa.field(screen(), "scenePoisoned"))
                && !ModernNavigationQa.root(screen()).getChildren().isEmpty(), "A visible settings screen lost its Compose scene");
    }
    private void resume() { require(screen() == null && minecraft.mouseHandler.isMouseGrabbed(), "Closing settings left the native screen/cursor active: " + screen()); }
    private void beginMovement() {
        resume(); before = minecraft.player.position(); key(forward, InputConstants.PRESS);
        require(minecraft.options.keyUp.isDown(), "Native movement press was blocked after settings closed");
    }
    private void endMovement() {
        require(minecraft.player.position().distanceToSqr(before) > .0001, "The native player did not move after settings closed");
        key(forward, InputConstants.RELEASE);
        require(!minecraft.options.keyUp.isDown(), "Native movement release was blocked after settings closed");
    }
    private void click(String label) throws Exception {
        var node = ModernNavigationQa.find(ModernNavigationQa.root(screen()), label);
        require(node != null, "Native composed control is absent: " + label);
        var bounds = node.getBoundsInWindow();
        ModernNavigationQa.pointer(minecraft, (bounds.getLeft() + bounds.getRight()) / 2, (bounds.getTop() + bounds.getBottom()) / 2);
    }
    private void clickNative(String label) {
        String translated = Component.translatable(label).getString();
        var widget = screen().children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(button -> button.active && button.getMessage().getString().startsWith(translated)).findFirst().orElseThrow(() -> new IllegalStateException("Native button is absent: " + label + " / " + screen().children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).map(button -> button.getMessage().getString()).toList()));
        double scale = minecraft.getWindow().getGuiScale();
        ModernNavigationQa.pointer(minecraft, (widget.getX() + widget.getWidth() / 2.0) * scale, (widget.getY() + widget.getHeight() / 2.0) * scale);
    }
    private void clickRendererClose() throws Exception {
        Object button = ModernNavigationQa.field(screen(), "closeButton");
        require(Boolean.TRUE.equals(button.getClass().getMethod("isEnabled").invoke(button)), "Sodium close button is disabled");
        Object dimensions = button.getClass().getMethod("getDimensions").invoke(button);
        double scale = minecraft.getWindow().getGuiScale();
        double x = ((Number) dimensions.getClass().getMethod("getCenterX").invoke(dimensions)).doubleValue() * scale;
        double y = ((Number) dimensions.getClass().getMethod("getCenterY").invoke(dimensions)).doubleValue() * scale;
        ModernNavigationQa.pointer(minecraft, x, y);
    }
    private void escape() { ModernNavigationQa.escape(minecraft); }
    private void key(int key, int action) { ((KeyboardQaAccess) minecraft.keyboardHandler).arcadium$nativeKey(minecraft.getWindow().handle(), action, new KeyEvent(key, 0, 0)); }
    private void capture(String name) {
        waiting = true;
        Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), image -> {
            try (image) {
                image.writeToFile(output.resolve(name + ".png"));
                QaTickScheduler.execute(() -> waiting = false);
            } catch (Throwable failure) { QaTickScheduler.execute(() -> finish(failure)); }
        });
    }
    private void finish(Throwable failure) {
        if (complete) return; complete = true;
        key(forward, InputConstants.RELEASE);
        report.put("passed", failure == null);
        if (failure != null) report.put("error", failure.toString());
        try { Files.writeString(output.resolve("screen-escape.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Throwable writing) { if (failure == null) failure = writing; else failure.addSuppressed(writing); }
        if (failure == null) completion.complete(report); else completion.completeExceptionally(failure);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
