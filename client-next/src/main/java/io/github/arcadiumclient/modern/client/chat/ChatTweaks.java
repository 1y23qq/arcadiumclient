// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.chat;

import io.github.arcadiumclient.modern.client.features.ChatFiltersConfig;
import io.github.arcadiumclient.modern.client.features.ChatTweaksConfig;
import io.github.arcadiumclient.modern.mixin.ChatAccess;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public final class ChatTweaks {
    private record Original(Component text, long time, int count) {}
    private static final Map<Component, Original> ORIGINALS = new IdentityHashMap<>();
    private static boolean forwarding;
    private static boolean refresh;
    private static String lastTimestamp = "";

    private ChatTweaks() {}

    public static boolean intercept(ChatComponent chat, Component message, MessageSignature signature, net.minecraft.client.multiplayer.chat.GuiMessageSource sourceType, GuiMessageTag tag) {
        if (forwarding) return false;
        String plain = message.getString();
        if (ChatFiltersConfig.hide(plain) || ChatTweaksConfig.getRemoveBlankMessages() && plain.isBlank()) return true;
        var access = (ChatAccess) chat;
        List<GuiMessage> messages = access.arcadium$messages();
        long now = System.currentTimeMillis();
        int count = 1;
        if (ChatTweaksConfig.getCompactChat() && !(ChatTweaksConfig.getDontCompactScreenshots() && plain.contains("Saved screenshot"))) {
            int line = 0;
            int messageIndex = 0;
            int limit = ChatTweaksConfig.getCompactChatLimit();
            Minecraft mc = Minecraft.getInstance();
            int width = limit == 0 ? 1 : Math.max(1, (int) Math.floor(ChatComponent.getWidth(mc.options.chatWidth().get()) / mc.options.chatScale().get()));
            for (GuiMessage old : List.copyOf(messages)) {
                Original source = ORIGINALS.get(old.content());
                int index = line;
                int sequence = messageIndex++;
                if (limit != 0) line += Math.max(1, old.splitLines(mc.font, width).size());
                if (source == null) continue;
                if (ChatTweaksConfig.getConsecutiveCompactChat() && sequence != 0) break;
                if (limit != 0 && index >= ChatTweaksConfig.getCompactChatLines()) break;
                if (limit != 1 && now - source.time() > ChatTweaksConfig.getCompactChatTime() * 1000L) break;
                if (!source.text().equals(message)) continue;
                count = Math.min(999999, source.count() + 1);
                messages.remove(old);
                ORIGINALS.remove(old.content());
                access.arcadium$refresh();
                break;
            }
        }
        MutableComponent displayed = displayEmoji(message);
        if (count > 1 && !ChatTweaksConfig.getHideDuplicateCount()) {
            String number = switch (ChatTweaksConfig.getCompactChatFormat()) {
                case 1 -> "[" + count + "]";
                case 2 -> "{" + count + "}";
                case 3 -> "<" + count + ">";
                case 4 -> "(x" + count + ")";
                case 5 -> "[x" + count + "]";
                case 6 -> "{x" + count + "}";
                case 7 -> "<x" + count + ">";
                case 8 -> "x" + count;
                case 9 -> count + "x";
                default -> "(" + count + ")";
            };
            displayed.append(Component.literal(" " + number).setStyle(Style.EMPTY.withColor(ChatTweaksConfig.getCompactChatColor().getRGB() & 0xFFFFFF)));
        }
        if (ChatTweaksConfig.getTimestamps()) {
            String pattern = ChatTweaksConfig.getTimestampsFormat() == 0 ? "hh:mm" : "HH:mm";
            if (ChatTweaksConfig.getSecondsOnTimestamps()) pattern += ":ss";
            String time = LocalTime.now().format(DateTimeFormatter.ofPattern(pattern));
            if (!ChatTweaksConfig.getOnlyNewTimestamps() || !lastTimestamp.equals(time)) {
                lastTimestamp = time;
                String left = switch (ChatTweaksConfig.getTimestampsBrackets()) { case 1 -> "("; case 2 -> "{"; case 3 -> "<"; case 4 -> ""; case 5 -> ChatTweaksConfig.getTimestampsLeftBracket(); default -> "["; };
                String right = switch (ChatTweaksConfig.getTimestampsBrackets()) { case 1 -> ")"; case 2 -> "}"; case 3 -> ">"; case 4 -> ""; case 5 -> ChatTweaksConfig.getTimestampsRightBracket(); default -> "]"; };
                Component stamp = Component.literal(left + time + right + " ").setStyle(Style.EMPTY.withColor(ChatTweaksConfig.getTimestampsColor().getRGB() & 0xFFFFFF));
                if (ChatTweaksConfig.getTimestampsStyle() == 0) displayed = Component.empty().append(stamp).append(displayed);
                else displayed = displayed.withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(stamp)));
            }
        }
        ORIGINALS.put(displayed, new Original(message.copy(), now, count));
        forwarding = true;
        try { access.arcadium$addMessage(displayed, signature, sourceType, tag); }
        finally { forwarding = false; }
        if (ORIGINALS.size() > ChatTweaksConfig.getIncreaseChatHistoryLimit() * 2)
            ORIGINALS.keySet().removeIf(key -> messages.stream().noneMatch(row -> row.content() == key));
        ChatHistory.changed();
        return true;
    }

    public static void requestRefresh() { refresh = true; }
    private static MutableComponent displayEmoji(Component original) {
        if (!ChatTweaksConfig.getEmoji()) return original.copy();
        MutableComponent result = Component.empty();
        original.visit((style, text) -> {
            result.append(Component.literal(org.polyfrost.oneconfig.internal.emoji.EmojiText.display(text)).setStyle(style));
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return result;
    }
    public static void tick() {
        if (refresh) {
            refresh = false;
            Minecraft.getInstance().gui.hud.getChat().rescaleChat();
        }
    }
    public static void clear() { ORIGINALS.clear(); lastTimestamp = ""; }
}
