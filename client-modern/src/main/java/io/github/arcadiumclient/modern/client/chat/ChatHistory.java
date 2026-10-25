// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.chat;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.arcadiumclient.modern.client.features.ChatTweaksConfig;
import io.github.arcadiumclient.modern.mixin.ChatAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ComponentSerialization;

public final class ChatHistory {
    private static final ChatHistoryStore STORE = new ChatHistoryStore(FabricLoader.getInstance().getConfigDir().resolve("arcadium/chat-history/1.21.11"));
    private static CompletableFuture<Void> pending = CompletableFuture.completedFuture(null);
    private static long due;
    private static boolean dirty;
    private static String server;
    private static Object level;
    private static boolean disconnecting;
    private ChatHistory() {}

    public static void changed() { dirty = true; }
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (level != mc.level) {
            flush();
            level = mc.level;
            if (level != null) {
                server = mc.getCurrentServer() == null ? "singleplayer" : mc.getCurrentServer().ip;
                if (ChatTweaksConfig.getSaveChatHistory()) load(server);
            }
        }
        if (System.nanoTime() >= due) {
            due = System.nanoTime() + Math.max(5, ChatTweaksConfig.getSaveChatHistoryInterval()) * 1_000_000_000L;
            flush();
        }
    }
    private static void load(String id) {
        Minecraft mc = Minecraft.getInstance();
        Object expected = level;
        pending = pending.exceptionally(failure -> null).thenRunAsync(() -> {
            try {
                var loaded = STORE.load(id);
                mc.execute(() -> {
                    if (level != expected || mc.level == null) return;
                    var chat = mc.gui.getChat();
                    var rows = ((ChatAccess) chat).arcadium$messages();
                    if (!rows.isEmpty()) return;
                    for (int i = loaded.size() - 1; i >= 0; i--) {
                        var json = JsonParser.parseString(loaded.get(i).json());
                        ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, json).result().ifPresent(chat::addMessage);
                    }
                    if (ChatTweaksConfig.getSessionMarkers()) chat.addMessage(net.minecraft.network.chat.Component.literal("----- New session -----").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                });
            } catch (Exception failure) { org.slf4j.LoggerFactory.getLogger("Arcadium/Chat").warn("Could not load chat history", failure); }
        });
    }
    public static void flush() {
        if (!dirty || server == null || !ChatTweaksConfig.getSaveChatHistory()) return;
        dirty = false;
        var mc = Minecraft.getInstance();
        var rows = ((ChatAccess) mc.gui.getChat()).arcadium$messages();
        var snapshot = new ArrayList<ChatHistoryStore.Entry>();
        for (var row : rows) ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, row.content()).result().ifPresent(json -> snapshot.add(new ChatHistoryStore.Entry(json.toString(), System.currentTimeMillis())));
        String owned = server;
        pending = pending.exceptionally(failure -> null).thenRunAsync(() -> {
            try { STORE.save(owned, snapshot); }
            catch (Exception failure) { org.slf4j.LoggerFactory.getLogger("Arcadium/Chat").warn("Could not save chat history", failure); }
        });
    }
    public static void beginDisconnect() { flush(); disconnecting = true; }
    public static void endDisconnect() { disconnecting = false; }
    public static boolean keepOnDisconnect() { return disconnecting && ChatTweaksConfig.getDontClearChatHistory(); }
    public static void close() {
        flush();
        try { pending.get(3, java.util.concurrent.TimeUnit.SECONDS); }
        catch (Exception failure) { org.slf4j.LoggerFactory.getLogger("Arcadium/Chat").warn("Could not finish saving chat history", failure); }
    }
}
