// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.gui.qa;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import dev.microcontrollers.confirmdisconnect.ConfirmDisconnectScreen;
import dev.microcontrollers.confirmdisconnect.config.ConfirmDisconnectConfig;
import dev.microcontrollers.confirmdisconnect.mixin.PauseScreenAccessor;
import io.github.arcadiumclient.modern.gui.ArcadiumPauseScreen;
import io.github.arcadiumclient.modern.gui.SkinChangerScreen;
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
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;

final class ModernPauseQa {
    private final Minecraft minecraft;
    private final Path output;
    private final ConfirmDisconnectConfig config = ConfirmDisconnectConfig.CONFIG.instance();
    private final boolean[] before = {config.confirmEnabled, config.useDiscreteConfirmation, config.enableInSingleplayer, config.enableInMultiplayer, config.confirmOnLeft};
    private final double delay = config.confirmDelay;
    private final Screen previous;
    private final CompletableFuture<Map<String, Object>> completion = new CompletableFuture<>();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final List<String> captures = new ArrayList<>();
    private final long started = System.nanoTime();
    private ArcadiumPauseScreen screen;
    private int phase, ticks;
    private boolean complete, waiting;

    static CompletableFuture<Map<String, Object>> run(Minecraft minecraft, Path output) {
        var qa = new ModernPauseQa(minecraft, output); qa.start(); return qa.completion;
    }
    private ModernPauseQa(Minecraft minecraft, Path output) {
        this.minecraft = minecraft; this.output = output; previous = minecraft.screen;
    }
    private void start() {
        try {
            require(minecraft.hasSingleplayerServer(), "Pause QA requires the isolated integrated world");
            minecraft.setScreen(null);
            escape();
            require(minecraft.screen instanceof ArcadiumPauseScreen, "Native Escape did not open the enhanced vanilla pause screen");
            screen = (ArcadiumPauseScreen) minecraft.screen;
            verifyLayout();
            config.confirmEnabled = true; config.enableInSingleplayer = true; config.enableInMultiplayer = false;
            config.useDiscreteConfirmation = false; config.confirmOnLeft = true; config.confirmDelay = .5;
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (complete || waiting) return;
                try { tick(); } catch (Throwable failure) { finish(failure); }
            });
        } catch (Throwable failure) { finish(failure); }
    }
    private void tick() throws Exception {
        require(System.nanoTime() - started < 35_000_000_000L, "Native pause QA timed out");
        require(minecraft.level != null && minecraft.player != null, "A confirmation-only action disconnected the native world");
        ticks++;
        switch (phase) {
            case 0 -> { if (ticks >= 12) capture("pause-native-layout"); }
            case 1 -> { click(disconnect()); require(minecraft.screen instanceof ConfirmDisconnectScreen, "Native Confirm Disconnect was bypassed"); next(); }
            case 2 -> {
                if (ticks < 2) return;
                require(buttons(minecraft.screen).stream().noneMatch(button -> button.active), "Native confirmation skipped its configured delay");
                capture("pause-confirm-delay-left");
            }
            case 3 -> {
                if (ticks < 12) return;
                require(buttons(minecraft.screen).stream().allMatch(button -> button.active), "Native confirmation delay did not expire through screen ticks");
                report.put("modalDelayNativeTicks", true);
                click(buttons(minecraft.screen).get(1));
                require(minecraft.screen == screen, "Native confirmation Back did not restore the same pause screen");
                verifyLayout(); config.useDiscreteConfirmation = true; config.confirmDelay = .5;
                click(disconnect()); next();
            }
            case 4 -> {
                if (ticks < 2) return;
                require(minecraft.screen == screen && !disconnect().active, "Native discrete confirmation did not disable the original button during its delay");
                capture("pause-discrete-delay");
            }
            case 5 -> {
                if (ticks < 12) return;
                require(disconnect().active, "Native discrete confirmation did not expire");
                report.put("discreteDelayNativeTicks", true);
                capture("pause-discrete-ready");
            }
            case 6 -> { click(named("Skins")); require(minecraft.screen instanceof SkinChangerScreen, "Native pause Skins button did not open Skins"); next(); }
            case 7 -> { if (ticks >= 12) { escape(); next(); } }
            case 8 -> {
                require(minecraft.screen == screen, "Native Escape from Skins did not restore the pause parent");
                verifyLayout(); escape(); next();
            }
            case 9 -> {
                require(minecraft.screen == null, "Native Escape did not resume the game");
                report.put("worldRemainedLoaded", true); report.put("skinsParentAndEscape", true); finish(null);
            }
            default -> throw new IllegalStateException("Unknown native pause QA phase");
        }
    }
    private void verifyLayout() {
        require(screen instanceof PauseScreen, "Pause screen does not inherit Minecraft's native layout");
        var buttons = buttons(screen);
        report.put("buttons", buttons.stream().map(button -> Map.of("label", button.getMessage().getString(), "bounds",
                List.of(button.getX(), button.getY(), button.getWidth(), button.getHeight()))).toList());
        for (String name : List.of("Arcadium Options", "Skins", "Accounts")) named(name);
        for (int i = 0; i < buttons.size(); i++) {
            var a = buttons.get(i);
            require(a.getHeight() == 20 && a.getY() >= 0 && a.getY() + a.getHeight() <= ((Screen) screen).height, "Native pause button is clipped: " + a.getMessage().getString());
            for (int j = i + 1; j < buttons.size(); j++) {
                var b = buttons.get(j);
                require(a.getX() + a.getWidth() <= b.getX() || b.getX() + b.getWidth() <= a.getX()
                        || a.getY() + a.getHeight() <= b.getY() || b.getY() + b.getHeight() <= a.getY(), "Native pause buttons overlap: " + a.getMessage().getString() + " / " + b.getMessage().getString());
            }
        }
        report.put("nativePauseLayout", true);
        report.put("buttons", buttons.stream().map(button -> Map.of("label", button.getMessage().getString(), "bounds",
                List.of(button.getX(), button.getY(), button.getWidth(), button.getHeight()))).toList());
    }
    private Button disconnect() { return ((PauseScreenAccessor) (Object) screen).getDisconnectButton(); }
    private static List<Button> buttons(Screen screen) { return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(button -> button.visible).toList(); }
    private Button named(String name) { return buttons(screen).stream().filter(button -> button.getMessage().getString().equals(name)).findFirst().orElseThrow(); }
    private void click(Button button) {
        var mouse = (MouseQaAccess) minecraft.mouseHandler;
        long window = minecraft.getWindow().handle(); double scale = minecraft.getWindow().getGuiScale();
        mouse.arcadium$nativeMove(window, (button.getX() + button.getWidth() / 2.0) * scale, (button.getY() + button.getHeight() / 2.0) * scale);
        var event = new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0);
        mouse.arcadium$nativeButton(window, event, InputConstants.PRESS);
        mouse.arcadium$nativeButton(window, event, InputConstants.RELEASE);
    }
    private void escape() {
        var key = (KeyboardQaAccess) minecraft.keyboardHandler; long window = minecraft.getWindow().handle();
        var event = new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0);
        key.arcadium$nativeKey(window, InputConstants.PRESS, event); key.arcadium$nativeKey(window, InputConstants.RELEASE, event);
    }
    private void capture(String name) {
        waiting = true;
        Screenshot.takeScreenshot(minecraft.getMainRenderTarget(), image -> {
            try (image) {
                image.writeToFile(output.resolve(name + ".png")); captures.add(name + ".png");
                QaTickScheduler.execute(() -> { waiting = false; next(); });
            } catch (Throwable failure) { QaTickScheduler.execute(() -> finish(failure)); }
        });
    }
    private void next() { phase++; ticks = 0; }
    private void finish(Throwable failure) {
        if (complete) return;
        complete = true;
        try {
            config.confirmEnabled = before[0]; config.useDiscreteConfirmation = before[1]; config.enableInSingleplayer = before[2];
            config.enableInMultiplayer = before[3]; config.confirmOnLeft = before[4]; config.confirmDelay = delay;
            minecraft.setScreen(previous);
            report.put("captures", captures); report.put("passed", failure == null); report.put("restored", true);
            if (failure != null) report.put("error", failure.toString());
            Files.writeString(output.resolve("pause-confirmation.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
            if (failure == null) completion.complete(report); else completion.completeExceptionally(failure);
        } catch (Throwable restoreFailure) { completion.completeExceptionally(restoreFailure); }
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
