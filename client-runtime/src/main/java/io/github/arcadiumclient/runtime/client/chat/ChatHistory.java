// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.chat;

import io.github.arcadiumclient.runtime.client.features.ChatTweaksConfig;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatMessage;
import net.minecraft.text.Formatting;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.apache.logging.log4j.LogManager;

public final class ChatHistory {
    private static final ChatHistoryStore STORE = new ChatHistoryStore(FabricLoader.getInstance().getConfigDir().resolve("arcadium/chat-history"));
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(8), task -> {
        Thread thread = new Thread(task, "Arcadium-Chat-History"); thread.setDaemon(true); return thread;
    });
    private static final WeakIdentityMap<Text, Long> ORIGIN = new WeakIdentityMap<>();
    private static final Set<String> INVALID = java.util.Collections.synchronizedSet(new HashSet<>());
    private static Object observedConnection;
    private static long observedGeneration, loadedGeneration = Long.MIN_VALUE, saveAt;
    private static String server;
    private static boolean restoring, loading;
    private record Snapshot(Text text, long time, int tick, int id) { }

    private ChatHistory() { }

    public static void observe(Text text, boolean refresh) {
        if (text == null || refresh || restoring) return;
        ORIGIN.put(text, generation());
        ChatTweaks.received(text);
    }

    private static long generation() {
        Object connection = Minecraft.getInstance().getNetworkHandler();
        if (connection != observedConnection) { observedConnection = connection; observedGeneration++; }
        return observedGeneration;
    }

    public static void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client.gui == null || client.world == null || client.player == null || client.getNetworkHandler() == null) return;
        ChatHistoryAccess access = (ChatHistoryAccess) client.gui.getChat();
        long connection = generation();
        if (loadedGeneration != connection) {
            loadedGeneration = connection;
            loading = false;
            String next = client.isSingleplayer() ? "world:" + client.world.getData().getName()
                    : client.getCurrentServerEntry() == null ? "server:unknown" : "server:" + client.getCurrentServerEntry().ip.toLowerCase(java.util.Locale.ROOT);
            server = next;
            if (ChatTweaksConfig.getSaveChatHistory()) {
                List<Snapshot> live = snapshot(access).stream().filter(entry -> ORIGIN.getOrDefault(entry.text, -1L) == connection).toList();
                access.arcadium$clearHistory();
                restore(access, live, false);
                loading = true;
                if (!submit(() -> load(next, connection))) { loading = false; sessionMarker(access); }
            } else sessionMarker(access);
            saveAt = System.nanoTime() + interval();
        }
        if (!loading && ChatTweaksConfig.getSaveChatHistory() && System.nanoTime() >= saveAt) {
            saveBeforeClear(access);
            saveAt = System.nanoTime() + interval();
        }
    }

    private static long interval() { return Math.max(5, Math.min(60, ChatTweaksConfig.getSaveChatHistoryInterval())) * 1_000_000_000L; }

    private static void load(String key, long connection) {
        List<Snapshot> saved;
        try {
            ArrayList<Snapshot> parsed = new ArrayList<>();
            for (ChatHistoryStore.Entry entry : STORE.load(key)) {
                if (parsed.size() == ChatTweaks.historyLimit()) break;
                Text text = Text.Serializer.fromJson(entry.json());
                if (text != null) parsed.add(new Snapshot(text, entry.received(), 0, 0));
            }
            saved = List.copyOf(parsed);
        }
        catch (Exception failure) {
            INVALID.add(key);
            LogManager.getLogger("Arcadium/Chat").warn("Could not read chat history; the existing file will be preserved", failure);
            saved = List.of();
        }
        List<Snapshot> result = saved;
        Minecraft.getInstance().executeTask(() -> {
            Minecraft client = Minecraft.getInstance();
            if (connection != generation() || client.gui == null || client.world == null) return;
            ChatHistoryAccess access = (ChatHistoryAccess) client.gui.getChat();
            List<Snapshot> live = snapshot(access);
            List<Snapshot> old = result.stream().map(entry -> new Snapshot(entry.text, entry.time, client.gui.getTicks() - 201, 0)).toList();
            access.arcadium$clearHistory();
            restore(access, old, true);
            restore(access, live, false);
            sessionMarker(access);
            loading = false;
        });
    }

    private static List<Snapshot> snapshot(ChatHistoryAccess access) {
        ArrayList<Snapshot> entries = new ArrayList<>();
        for (ChatMessage message : access.arcadium$history()) {
            if (entries.size() == ChatTweaks.historyLimit()) break;
            entries.add(new Snapshot(message.getText(), ChatTweaks.received(message.getText()), message.getTimeOfCreation(), message.getId()));
        }
        return entries;
    }

    private static void restore(ChatHistoryAccess access, List<Snapshot> entries, boolean archived) {
        restoring = true;
        try {
            for (int i = entries.size() - 1; i >= 0; i--) {
                Snapshot entry = entries.get(i);
                Text copy = entry.text.copy();
                if (!archived) ORIGIN.put(copy, observedGeneration);
                access.arcadium$restore(copy, entry.time, entry.tick, archived ? 0 : entry.id);
            }
        } finally { restoring = false; }
    }

    private static void sessionMarker(ChatHistoryAccess access) {
        if (!ChatTweaksConfig.getSaveChatHistory() || !ChatTweaksConfig.getSessionMarkers()) return;
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        Text text = new LiteralText("Connected at " + date).setStyle(new Style().setColor(Formatting.DARK_GRAY));
        access.arcadium$restore(text, System.currentTimeMillis(), Minecraft.getInstance().gui.getTicks());
    }

    public static void saveBeforeClear(ChatHistoryAccess access) {
        if (restoring || server == null || INVALID.contains(server) || !ChatTweaksConfig.getSaveChatHistory()) return;
        String destination = server;
        boolean mergeArchive = loading;
        List<Snapshot> entries = snapshot(access).stream().map(entry -> new Snapshot(entry.text.copy(), entry.time, entry.tick, entry.id)).toList();
        if (entries.isEmpty()) return;
        submit(() -> {
            try {
                if (INVALID.contains(destination)) return;
                ArrayList<ChatHistoryStore.Entry> serialized = new ArrayList<>(entries.size());
                for (Snapshot entry : entries) serialized.add(new ChatHistoryStore.Entry(Text.Serializer.toJson(entry.text), entry.time));
                if (mergeArchive) serialized.addAll(STORE.load(destination));
                STORE.save(destination, serialized);
            } catch (Exception failure) { LogManager.getLogger("Arcadium/Chat").warn("Could not save chat history", failure); }
        });
    }

    public static void close() {
        Minecraft client = Minecraft.getInstance();
        if (client.gui != null) saveBeforeClear((ChatHistoryAccess) client.gui.getChat());
        IO.shutdown();
        try { IO.awaitTermination(5, TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }

    private static boolean submit(Runnable action) {
        try { IO.execute(action); return true; }
        catch (RejectedExecutionException ignored) { return false; }
    }
}
