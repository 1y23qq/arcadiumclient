// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.gui.qa;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.arcadiumclient.modern.client.BadgeText;
import io.github.arcadiumclient.modern.gui.qa.mixin.KeyboardQaAccess;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUI;
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen;

final class ModernChatInputQa {
    private final Minecraft minecraft;
    private final Path output;
    private final CompletableFuture<Map<String, Object>> completion = new CompletableFuture<>();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final ArrayList<Map<String, Object>> stages = new ArrayList<>();
    private final String originalChatKey;
    private final int chatKey;
    private final int forwardKey;
    private int step;
    private int waits;
    private boolean closed;

    static CompletableFuture<Map<String, Object>> run(Minecraft minecraft, Path output) {
        try {
            ModernChatInputQa qa = new ModernChatInputQa(minecraft, output);
            qa.start();
            return qa.completion;
        } catch (Throwable failure) { return CompletableFuture.failedFuture(failure); }
    }

    private ModernChatInputQa(Minecraft minecraft, Path output) {
        this.minecraft = minecraft;
        this.output = output;
        originalChatKey = minecraft.options.keyChat.saveString();
        chatKey = InputConstants.getKey(originalChatKey).getValue();
        forwardKey = InputConstants.getKey(minecraft.options.keyUp.saveString()).getValue();
    }

    private void start() {
        require(minecraft.player != null && minecraft.level != null, "Chat input proof requires a real loaded world");
        require(chatKey > 0 && forwardKey > 0, "Chat/forward native controls are unbound");
        report.put("stages", stages);
        report.put("chatBinding", originalChatKey);
        report.put("forwardBinding", minecraft.options.keyUp.saveString());
        report.put("chattingLoaded", net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("chatting"));
        verifyBadge();
        minecraft.gui.setScreen(null);
        minecraft.mouseHandler.grabMouse();
        QaTickScheduler.execute(this::tick);
    }

    private void tick() {
        if (closed) return;
        try {
            if (minecraft.gui.overlay() != null || ++waits < 4) { QaTickScheduler.execute(this::tick); return; }
            waits = 0;
            snapshot();
            switch (step++) {
                case 0 -> { require(minecraft.gui.screen() == null, "The native game did not resume"); key(forwardKey, GLFW.GLFW_PRESS); }
                case 1 -> {
                    require(minecraft.options.keyUp.isDown(), "Native forward press was not received");
                    tap(chatKey);
                }
                case 2 -> {
                    require(minecraft.gui.screen() instanceof ChatScreen, "Native chat binding did not open ChatScreen");
                    require(!minecraft.options.keyUp.isDown(), "Opening chat did not release movement");
                    key(forwardKey, GLFW.GLFW_RELEASE);
                    tap(GLFW.GLFW_KEY_ESCAPE);
                }
                case 3 -> {
                    require(minecraft.gui.screen() == null, "Escape did not close native chat");
                    require(!minecraft.options.keyUp.isDown(), "Movement remained held after chat closed");
                    tap(chatKey);
                }
                case 4 -> {
                    require(minecraft.gui.screen() instanceof ChatScreen, "Native chat did not reopen");
                    input().setValue("/say ARCADIUM_INPUT_QA");
                    tap(GLFW.GLFW_KEY_ENTER);
                }
                case 5 -> {
                    require(minecraft.gui.screen() == null, "Enter did not close native chat after sending");
                    key(forwardKey, GLFW.GLFW_PRESS);
                }
                case 6 -> {
                    require(minecraft.options.keyUp.isDown(), "Movement no longer worked after sending chat");
                    key(forwardKey, GLFW.GLFW_RELEASE);
                    minecraft.options.keyChat.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F12));
                    KeyMapping.resetMapping();
                    tap(GLFW.GLFW_KEY_F12);
                }
                case 7 -> {
                    require(minecraft.gui.screen() instanceof ChatScreen, "Rebound native chat control did not open chat");
                    require(!minecraft.options.keyUp.isDown(), "Movement release was lost across a screen transition");
                    tap(GLFW.GLFW_KEY_ESCAPE);
                }
                case 8 -> {
                    require(minecraft.gui.screen() == null, "Rebound chat did not close");
                    minecraft.options.keyChat.setKey(InputConstants.getKey(originalChatKey));
                    KeyMapping.resetMapping();
                    OneConfigUI.open();
                }
                case 9 -> {
                    require(minecraft.gui.screen() instanceof ComposeScreen, "Arcadium Options did not open");
                    tap(GLFW.GLFW_KEY_ESCAPE);
                    waits = -40;
                }
                case 10 -> {
                    require(minecraft.gui.screen() == null, "Escape did not resume the game from Arcadium Options");
                    tap(chatKey);
                }
                case 11 -> {
                    require(minecraft.gui.screen() instanceof ChatScreen, "Chat would not open after closing Arcadium Options");
                    input().setValue("/say ARCADIUM_AFTER_OPTIONS_QA");
                    tap(GLFW.GLFW_KEY_ENTER);
                }
                case 12 -> {
                    require(minecraft.gui.screen() == null, "Chat would not close after returning from Arcadium Options");
                    key(forwardKey, GLFW.GLFW_PRESS);
                }
                case 13 -> {
                    require(minecraft.options.keyUp.isDown(), "Movement stopped working after closing Arcadium Options");
                    key(forwardKey, GLFW.GLFW_RELEASE);
                    tap(chatKey);
                }
                case 14 -> {
                    require(minecraft.gui.screen() instanceof ChatScreen, "Repeated native chat open failed");
                    tap(GLFW.GLFW_KEY_ESCAPE);
                }
                case 15 -> {
                    require(minecraft.gui.screen() == null, "Repeated native chat close failed");
                    require(!minecraft.options.keyUp.isDown(), "Movement remained held after repeated chat/options transitions");
                    report.put("passed", true);
                    finish(null);
                    return;
                }
                default -> throw new IllegalStateException("Unexpected native chat proof stage");
            }
            QaTickScheduler.execute(this::tick);
        } catch (Throwable failure) { finish(failure); }
    }

    private EditBox input() { return (EditBox) minecraft.gui.screen().getFocused(); }

    private void verifyBadge() {
        Component original = Component.literal("Arcadium").withStyle(net.minecraft.ChatFormatting.BOLD);
        Component prefixed = BadgeText.prefix(original);
        int added = minecraft.font.width(prefixed) - minecraft.font.width(original);
        report.put("badgeAdvance", added);
        require(added == BadgeText.ADVANCE, "The native badge advance differs from legacy: " + added);
        require(prefixed.getSiblings().get(1).equals(original), "The badge changed the original name style");
    }

    private void tap(int code) { key(code, GLFW.GLFW_PRESS); key(code, GLFW.GLFW_RELEASE); }

    private void key(int code, int action) {
        ((KeyboardQaAccess) minecraft.keyboardHandler).arcadium$nativeKey(minecraft.getWindow().handle(), action, new KeyEvent(code, 0, 0));
    }

    private void snapshot() {
        stages.add(Map.of("step", step, "screen", minecraft.gui.screen() == null ? "none" : minecraft.gui.screen().getClass().getName(),
                "forwardDown", minecraft.options.keyUp.isDown(), "chatDown", minecraft.options.keyChat.isDown(),
                "mouseGrabbed", minecraft.mouseHandler.isMouseGrabbed(), "renderedFrames", ModernFrameSampler.renderedFrames()));
    }

    private void finish(Throwable failure) {
        closed = true;
        try {
            key(forwardKey, GLFW.GLFW_RELEASE);
            key(chatKey, GLFW.GLFW_RELEASE);
            key(GLFW.GLFW_KEY_F12, GLFW.GLFW_RELEASE);
            minecraft.options.keyChat.setKey(InputConstants.getKey(originalChatKey));
            KeyMapping.resetMapping();
            report.put("restored", true);
            if (failure != null) { report.put("passed", false); report.put("error", failure.toString()); }
            Files.writeString(output.resolve("chat-input.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Throwable cleanup) { if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup); }
        if (failure == null) completion.complete(report); else completion.completeExceptionally(failure);
    }

    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
