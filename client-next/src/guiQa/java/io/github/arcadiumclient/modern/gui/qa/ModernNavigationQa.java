// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.gui.qa;

import androidx.compose.ui.geometry.Rect;
import androidx.compose.ui.node.RootNodeOwner;
import androidx.compose.ui.semantics.SemanticsNode;
import androidx.compose.ui.semantics.SemanticsProperties;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.arcadiumclient.modern.gui.ArcadiumMainMenu;
import io.github.arcadiumclient.modern.client.BadgeText;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import io.github.arcadiumclient.modern.gui.ArcadiumServersScreen;
import io.github.arcadiumclient.modern.gui.ArcadiumWorldsScreen;
import io.github.arcadiumclient.modern.gui.qa.mixin.KeyboardQaAccess;
import io.github.arcadiumclient.modern.gui.qa.mixin.MouseQaAccess;
import java.net.InetAddress;
import java.net.ServerSocket;
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
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen;
import org.polyfrost.oneconfig.internal.ui.compose.impls.OneConfigUIScreen;

final class ModernNavigationQa {
    private final Minecraft minecraft;
    private final Path output;
    private final CompletableFuture<Map<String, Object>> completion = new CompletableFuture<>();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final List<Map<String, Object>> stages = new ArrayList<>();
    private final long started = System.nanoTime();
    private ArcadiumServersScreen servers;
    private int phase, ticks, cycle;
    private boolean complete, waiting;

    static CompletableFuture<Map<String, Object>> run(Minecraft minecraft, Path output) {
        var qa = new ModernNavigationQa(minecraft, output);
        qa.start();
        return qa.completion;
    }

    private ModernNavigationQa(Minecraft minecraft, Path output) { this.minecraft = minecraft; this.output = output; }
    private Screen screen() { return minecraft.gui.screen(); }
    private void display(Screen screen) { minecraft.gui.setScreen(screen); }
    private void start() {
        try {
            require(minecraft.level == null, "Navigation proof requires the title menu");
            Component original = Component.literal("Arcadium").withStyle(ChatFormatting.BOLD);
            int advance = minecraft.font.width(BadgeText.prefix(original)) - minecraft.font.width(original);
            require(advance == BadgeText.ADVANCE, "The native badge advance differs from legacy: " + advance);
            report.put("badgeAdvance", advance);
            display(new TitleScreen());
            report.put("input", "Native KeyboardHandler.keyPress and MouseHandler.onMove/onButton");
            report.put("stages", stages);
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (complete || waiting) return;
                try { tick(); } catch (Throwable failure) { finish(failure); }
            });
        } catch (Throwable failure) { finish(failure); }
    }

    private void tick() throws Exception {
        require(System.nanoTime() - started < 100_000_000_000L, "Navigation proof timed out in phase " + phase);
        if (++ticks < 12 || minecraft.gui.overlay() != null) return;
        switch (phase) {
            case 0 -> { title(); click("Arcadium Options"); next(); }
            case 1 -> { require(screen() instanceof OneConfigUIScreen, "Title button did not open Arcadium Options"); alive(); escape(); next(); }
            case 2 -> {
                if (screen() instanceof OneConfigUIScreen && ticks < 100) return;
                title(); capture("title-after-options-" + cycle, "Singleplayer"); next();
            }
            case 3 -> { title(); click("Singleplayer"); next(); }
            case 4 -> { require(screen() instanceof ArcadiumWorldsScreen, "Singleplayer did not open the Arcadium world browser"); alive(); escape(); next(); }
            case 5 -> { title(); click("Multiplayer"); next(); }
            case 6 -> {
                require(screen() instanceof ArcadiumServersScreen, "Multiplayer did not open the Arcadium server browser");
                alive(); servers = (ArcadiumServersScreen) screen(); click("Direct connect"); next();
            }
            case 7 -> { require(screen() == servers, "Opening direct connect lost the server browser"); alive(); escape(); next(); }
            case 8 -> {
                require(screen() == servers, "Escape closed the server browser instead of its dialog");
                alive();
                int port;
                try (var socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) { port = socket.getLocalPort(); }
                String address = "127.0.0.1:" + port;
                ConnectScreen.startConnecting(servers, minecraft, ServerAddress.parseString(address),
                        new ServerData("Navigation QA", address, ServerData.Type.OTHER), false, null);
                next();
            }
            case 9 -> {
                if (!(screen() instanceof DisconnectedScreen) && ticks < 200) return;
                require(screen() instanceof DisconnectedScreen, "Native refused connection did not reach the disconnect screen");
                Button back = screen().children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(button -> button.active).findFirst().orElseThrow();
                clickNative(back); next();
            }
            case 10 -> {
                require(screen() == servers, "Native disconnect Back did not restore the same themed multiplayer screen");
                alive(); capture("servers-after-disconnect-" + cycle, "Direct connect"); next();
            }
            case 11 -> { escape(); next(); }
            case 12 -> { title(); escape(); next(); }
            case 13 -> { title(); capture("title-after-servers-" + cycle, "Multiplayer"); next(); }
            case 14 -> { title(); click("Background"); next(); }
            case 15 -> { require(screen() instanceof io.github.arcadiumclient.modern.gui.TitleBackgroundScreen, "Background button did not open settings"); alive(); escape(); next(); }
            case 16 -> { title(); click("Background"); next(); }
            case 17 -> { require(screen() instanceof io.github.arcadiumclient.modern.gui.TitleBackgroundScreen, "Background settings did not reopen"); alive(); click("Back"); next(); }
            case 18 -> { title(); openChatShortcuts(); next(); }
            case 19 -> { require(screen().getClass().getName().equals("org.polyfrost.chatting.config.shortcut.ChatShortcutManagerScreen"), "Native chat shortcuts did not open"); alive(); escape(); next(); }
            case 20 -> {
                if (screen() instanceof OneConfigUIScreen) {
                    if (ticks == 12) { alive(); escape(); }
                    require(ticks < 100, "The native Chatting parent did not close after Escape");
                    return;
                }
                title(); openChatShortcuts(); next();
            }
            case 21 -> { require(screen().getClass().getName().equals("org.polyfrost.chatting.config.shortcut.ChatShortcutManagerScreen"), "Native chat shortcuts did not reopen"); alive(); display(new TitleScreen()); next(); }
            case 22 -> {
                title();
                if (++cycle < 2) { phase = 0; ticks = 0; }
                else next();
            }
            case 23 -> { report.put("repeatCycles", cycle); report.put("nativeDisconnectBack", true); report.put("backgroundEscapeAndClose", true); report.put("upstreamRemovedCloseAndEscape", true); finish(null); }
            default -> throw new IllegalStateException("Unknown navigation phase " + phase);
        }
    }

    private void title() throws Exception { require(screen() instanceof ArcadiumMainMenu, "Return reached a vanilla or absent title: " + screen()); alive(); }
    private void openChatShortcuts() throws Exception {
        display((Screen) Class.forName("org.polyfrost.chatting.config.shortcut.ChatShortcutManagerScreen").getConstructor().newInstance());
    }
    private void alive() throws Exception {
        require(screen() instanceof ComposeScreen, "Expected a Compose screen");
        require(field(screen(), "sceneOrNull") != null && Boolean.TRUE.equals(field(screen(), "showing"))
                        && !Boolean.TRUE.equals(field(screen(), "scenePoisoned")), "The current screen has no live Compose scene: " + screen());
        require(!root().getChildren().isEmpty(), "The current screen has no composed controls");
        stages.add(Map.of("phase", phase, "cycle", cycle, "screen", screen().getClass().getSimpleName(), "liveScene", true));
    }

    private SemanticsNode root() throws Exception { return root(screen()); }
    static SemanticsNode root(Screen screen) throws Exception {
        Object scene = field(screen, "sceneOrNull");
        return ((RootNodeOwner) field(scene, "mainOwner")).getSemanticsOwner().getRootSemanticsNode();
    }
    static SemanticsNode find(SemanticsNode node, String text) {
        var config = node.getConfig();
        var description = SemanticsProperties.INSTANCE.getContentDescription();
        if (config.contains(description) && config.get(description).contains(text)) return node;
        var key = SemanticsProperties.INSTANCE.getText();
        if (config.contains(key) && config.get(key).stream().anyMatch(value -> value.getText().equals(text))) return node;
        for (var child : node.getChildren()) { var match = find(child, text); if (match != null) return match; }
        return null;
    }
    private Rect bounds(String text) throws Exception {
        var node = find(root(), text);
        require(node != null, "Composed button is absent: " + text);
        Rect bounds = node.getBoundsInWindow();
        require(bounds.getWidth() > 0 && bounds.getHeight() > 0, "Composed button has empty bounds: " + text);
        return bounds;
    }
    private void click(String text) throws Exception {
        Rect bounds = bounds(text);
        pointer((bounds.getLeft() + bounds.getRight()) / 2, (bounds.getTop() + bounds.getBottom()) / 2);
    }
    private void clickNative(Button button) {
        double scale = minecraft.getWindow().getGuiScale();
        pointer((button.getX() + button.getWidth() / 2.0) * scale, (button.getY() + button.getHeight() / 2.0) * scale);
    }
    private void pointer(double x, double y) { pointer(minecraft, x, y); }
    static void pointer(Minecraft minecraft, double x, double y) {
        var mouse = (MouseQaAccess) minecraft.mouseHandler;
        long window = minecraft.getWindow().handle();
        mouse.arcadium$nativeMove(window, x, y);
        var button = new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0);
        mouse.arcadium$nativeButton(window, button, InputConstants.PRESS);
        mouse.arcadium$nativeButton(window, button, InputConstants.RELEASE);
    }
    private void escape() { escape(minecraft); }
    static void escape(Minecraft minecraft) {
        var keyboard = (KeyboardQaAccess) minecraft.keyboardHandler;
        long window = minecraft.getWindow().handle();
        var event = new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0);
        keyboard.arcadium$nativeKey(window, InputConstants.PRESS, event);
        keyboard.arcadium$nativeKey(window, InputConstants.RELEASE, event);
    }
    private void capture(String name, String label) throws Exception {
        Rect bounds = bounds(label);
        waiting = true;
        Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), image -> {
            try (image) {
                int pixels = 0;
                double sx = (double) image.getWidth() / minecraft.getWindow().getScreenWidth();
                double sy = (double) image.getHeight() / minecraft.getWindow().getScreenHeight();
                for (int y = Math.max(0, (int) (bounds.getTop() * sy)); y < Math.min(image.getHeight(), (int) Math.ceil(bounds.getBottom() * sy)); y++)
                    for (int x = Math.max(0, (int) (bounds.getLeft() * sx)); x < Math.min(image.getWidth(), (int) Math.ceil(bounds.getRight() * sx)); x++) {
                        int color = image.getPixel(x, y);
                        if ((color >>> 16 & 255) > 165 && (color >>> 8 & 255) > 165 && (color & 255) > 165) pixels++;
                    }
                image.writeToFile(output.resolve(name + ".png"));
                require(pixels >= 8, "Composed native button glyph pixels are absent after navigation: " + name);
                int count = pixels;
                QaTickScheduler.execute(() -> { report.put(name, Map.of("image", name + ".png", "buttonGlyphPixels", count)); waiting = false; });
            } catch (Throwable failure) { QaTickScheduler.execute(() -> finish(failure)); }
        });
    }
    static Object field(Object instance, String name) throws Exception {
        Class<?> type = instance.getClass();
        while (type != null) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(instance); }
            catch (NoSuchFieldException absent) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
    private void next() { phase++; ticks = 0; }
    private void finish(Throwable failure) {
        if (complete) return;
        complete = true;
        report.put("passed", failure == null);
        if (failure != null) report.put("error", failure.toString());
        try { Files.writeString(output.resolve("navigation.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Throwable writing) { if (failure == null) failure = writing; else failure.addSuppressed(writing); }
        if (failure == null) completion.complete(report); else completion.completeExceptionally(failure);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
