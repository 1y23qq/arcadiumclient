// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.bedwars;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

final class NickBotController {
    private final BedwarsOverlayConfig config;
    private final Consumer<String> message;
    private boolean running;
    private boolean waiting;
    private boolean selector;
    private long requestedAt;
    private long selectorAt;
    private long lastLobby;
    private int ticks;
    private int lastRequest;
    private int attempts;
    private int generated;
    private int previousSlot = -1;
    private String current = "";
    private Object lastBook;

    NickBotController(BedwarsOverlayConfig config, Consumer<String> message) { this.config = config; this.message = message; }

    void worldChanged() { selector = false; previousSlot = -1; lastBook = null; }

    void tick(boolean hypixel) {
        if (!config.enabled || !config.nickBot || !hypixel || BedwarsRuntime.world() == null) {
            if (running) { stop(); message.accept("Nick Bot disabled."); }
            return;
        }
        long now = System.currentTimeMillis();
        if (!running) {
            if (!BedwarsRuntime.lobby() && !BedwarsRuntime.limbo()) return;
            running = true; waiting = true; requestedAt = now;
            ticks = 0; lastRequest = 0; attempts = 0; generated = 0; current = "";
            BedwarsRuntime.command("/nick reuse");
            message.accept("Nick Bot: fetching current nickname.");
            return;
        }
        if (waiting) {
            if (now - requestedAt < 5000) return;
            waiting = false;
            message.accept("Nick Bot: continuing without a current nickname response.");
        }
        if (!BedwarsRuntime.lobby() && !BedwarsRuntime.limbo()) return;
        ticks++;
        Object screen = BedwarsRuntime.screen();
        if (screen != null && screen == lastBook) { BedwarsRuntime.closeBook(screen); return; }
        if (screen != lastBook) lastBook = null;
        String nick = BedwarsRuntime.bookNick(screen);
        if (!nick.isEmpty()) {
            lastBook = screen;
            if (wanted(nick)) {
                if (config.nickAutoClaim) {
                    BedwarsRuntime.command("/nick actuallyset " + nick);
                    message.accept("Nick Bot: requested nickname §f" + nick + "§7.");
                } else message.accept("Nick Bot: matched nickname §f" + nick + "§7.");
                BedwarsRuntime.closeBook(screen);
                config.nickBot = false;
                stop();
            } else {
                message.accept("Nick Bot #" + (++generated) + ": §f" + nick + "§7.");
                BedwarsRuntime.closeBook(screen);
            }
            return;
        }
        if (!BedwarsRuntime.lobby()) {
            if (BedwarsRuntime.limbo() && now - lastLobby > 5000) {
                List<String> lobbies = PlayerNameUtil.entries(config.nickLobbies);
                lobbies.removeIf(value -> !value.matches("[a-z0-9_]{1,20}"));
                if (lobbies.isEmpty()) lobbies = java.util.Collections.singletonList("bw");
                BedwarsRuntime.command("/l " + lobbies.get(ThreadLocalRandom.current().nextInt(lobbies.size())));
                lastLobby = now;
            }
            return;
        }
        if (selector) {
            if (BedwarsRuntime.selectLobby() || now - selectorAt > 5000) {
                selector = false;
                restoreSlot();
            }
            return;
        }
        if (screen != null) return;
        if (ticks - lastRequest < Math.max(1, config.nickInterval)) return;
        lastRequest = ticks;
        int reset = Math.max(0, config.nickResetInterval);
        if (reset > 0 && ++attempts > reset) {
            previousSlot = BedwarsRuntime.selectedSlot();
            BedwarsRuntime.openLobbySelector();
            selector = true; selectorAt = now; attempts = 0;
        } else BedwarsRuntime.command("/nick help setrandom");
    }

    boolean chat(String text) {
        if (!running) return false;
        if (waiting && text.startsWith("You are now nicked as ")) {
            current = text.substring("You are now nicked as ".length()).replaceAll("\\.$", "");
            waiting = false;
            message.accept("Nick Bot: current nickname §f" + current + "§7.");
            return true;
        }
        return waiting && text.equals("Processing request. Please wait...")
                || !waiting && text.equals("Generating a unique random name. Please wait...");
    }

    private boolean wanted(String nick) {
        return NickRules.matches(nick, current, config.nickFourLetters, config.nickVowels, config.nickUnderscores, config.nickCustom,
                PlayerNameUtil.entries(config.nickExact), PlayerNameUtil.entries(config.nickStartsWith),
                PlayerNameUtil.entries(config.nickEndsWith), PlayerNameUtil.entries(config.nickContains));
    }

    private void restoreSlot() { if (previousSlot >= 0) BedwarsRuntime.selectSlot(previousSlot); previousSlot = -1; }
    private void stop() { running = false; waiting = false; selector = false; lastBook = null; restoreSlot(); }
}
