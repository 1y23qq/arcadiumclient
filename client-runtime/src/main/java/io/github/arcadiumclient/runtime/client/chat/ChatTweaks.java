// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.chat;

import io.github.arcadiumclient.runtime.client.features.ChatTweaksConfig;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatMessage;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

public final class ChatTweaks {
    private static final WeakIdentityMap<Text, Long> RECEIVED = new WeakIdentityMap<>();
    private static boolean refresh;
    private final LinkedHashMap<String, Group> groups = new LinkedHashMap<>();
    private String previousKey, previousTime;

    public static final class Group {
        public final Set<Text> lines = Collections.newSetFromMap(new IdentityHashMap<>());
        private long count = 1;
        private long received;
        private Group(long received) { this.received = received; }
    }

    public record Display(Text text, Group group, boolean hidden) { }

    public static int historyLimit() { return Math.max(100, Math.min(16384, ChatTweaksConfig.getIncreaseChatHistoryLimit())); }
    public static void requestRefresh() { refresh = true; }
    public static void tick() {
        if (!refresh) return;
        refresh = false;
        Minecraft client = Minecraft.getInstance();
        if (client.gui != null) client.gui.getChat().reset();
    }

    public static long received(Text text) { return RECEIVED.computeIfAbsent(text, unused -> System.currentTimeMillis()); }
    public static void received(Text text, long time) { RECEIVED.put(text, time); }

    public void reset() { groups.clear(); previousKey = previousTime = null; }

    public Display prepare(Text raw, int id, int tick, List<ChatMessage> visible) {
        if (raw == null) return new Display(new LiteralText(""), null, true);
        if (ChatTweaksConfig.getRemoveBlankMessages() && raw.getString().isBlank()) return new Display(raw, null, true);
        String key = Text.Serializer.toJson(raw);
        long received = received(raw);
        boolean screenshot = containsFileLink(raw);
        boolean compact = id == 0 && ChatTweaksConfig.getCompactChat()
                && !(ChatTweaksConfig.getDontCompactScreenshots() && screenshot);
        Group group = null;
        if (compact) {
            group = groups.remove(key);
            long elapsed = group == null ? Long.MAX_VALUE : received - group.received;
            boolean consecutive = !ChatTweaksConfig.getConsecutiveCompactChat() || key.equals(previousKey);
            boolean withinTime = elapsed >= 0 && elapsed <= Math.max(1, Math.min(120, ChatTweaksConfig.getCompactChatTime())) * 1000L;
            int limit = ChatTweaksConfig.getCompactChatLimit();
            boolean withinLines = group != null && (limit == 0 || withinLines(group, visible));
            boolean withinWindow = switch (limit) {
                case 1 -> withinLines;
                case 2 -> withinTime && withinLines;
                default -> withinTime;
            };
            if (group == null || !withinWindow || !consecutive) {
                group = new Group(received);
            } else {
                Group duplicate = group;
                visible.removeIf(line -> duplicate.lines.contains(line.getText()));
                group.lines.clear();
                group.count = Math.min(Long.MAX_VALUE - 1, group.count) + 1;
                group.received = received;
            }
            groups.put(key, group);
            while (groups.size() > historyLimit()) groups.remove(groups.keySet().iterator().next());
        }
        previousKey = key;
        Text output = new LiteralText("").append(raw.copy());
        if (group != null && group.count > 1 && !ChatTweaksConfig.getHideDuplicateCount()) {
            output.append(new LiteralText(" " + ChatTextMarks.COUNTER
                    + ChatTextMarks.counter(group.count, ChatTweaksConfig.getCompactChatFormat()) + ChatTextMarks.END)
                    .setStyle(new Style()));
        }
        if (ChatTweaksConfig.getTimestamps()) {
            String stamp = timestamp(received(raw));
            if (ChatTweaksConfig.getTimestampsStyle() == 1) {
                if (output.getStyle().getHoverEvent() == null) output.setStyle(output.getStyle().deepCopy()
                        .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new LiteralText(stamp))));
            } else {
                String label = brackets(stamp);
                if (ChatTweaksConfig.getOnlyNewTimestamps() && label.equals(previousTime)) {
                    int width = Minecraft.getInstance().textRenderer.getWidth(label + " ");
                    int space = Math.max(1, Minecraft.getInstance().textRenderer.getWidth(" "));
                    label = " ".repeat((width + space - 1) / space);
                } else label += " ";
                previousTime = brackets(stamp);
                output = new LiteralText("" + ChatTextMarks.TIMESTAMP + label + ChatTextMarks.END).append(output);
            }
        }
        return new Display(output, group, false);
    }

    private static boolean withinLines(Group group, List<ChatMessage> visible) {
        int limit = Math.min(visible.size(), Math.max(1, Math.min(1000, ChatTweaksConfig.getCompactChatLines())));
        for (int index = 0; index < limit; index++) {
            if (group.lines.contains(visible.get(index).getText())) return true;
        }
        return false;
    }

    public void wrapped(Display display, List<Text> lines) {
        char continued = 0;
        for (int i = 0; i < lines.size(); i++) {
            Text line = lines.get(i);
            char entry = continued;
            for (char value : line.getString().toCharArray()) {
                if (value == ChatTextMarks.COUNTER || value == ChatTextMarks.TIMESTAMP) continued = value;
                else if (value == ChatTextMarks.END) continued = 0;
            }
            if (entry != 0) line = new LiteralText(String.valueOf(entry)).append(line);
            if (continued != 0) line = line.copy().append(String.valueOf(ChatTextMarks.END));
            lines.set(i, line);
            if (display.group != null) display.group.lines.add(line);
        }
    }

    private static String timestamp(long millis) {
        String pattern = ChatTweaksConfig.getTimestampsFormat() == 1 ? "HH:mm" : "hh:mm";
        if (ChatTweaksConfig.getSecondsOnTimestamps()) pattern += ":ss";
        if (ChatTweaksConfig.getTimestampsFormat() == 0) pattern += " a";
        return DateTimeFormatter.ofPattern(pattern, Locale.ROOT).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis));
    }

    private static String brackets(String stamp) {
        int choice = ChatTweaksConfig.getTimestampsBrackets();
        if (choice == 4) return stamp;
        if (choice == 5) return safeBracket(ChatTweaksConfig.getTimestampsLeftBracket()) + stamp + safeBracket(ChatTweaksConfig.getTimestampsRightBracket());
        choice = Math.max(0, Math.min(3, choice));
        return "[({<".charAt(choice) + stamp + "])}>".charAt(choice);
    }

    private static String safeBracket(String value) { return value == null ? "" : value.substring(0, Math.min(24, value.length())); }

    private static boolean containsFileLink(Text text) {
        ClickEvent event = text.getStyle().getClickEvent();
        if (event != null && event.getAction() == ClickEvent.Action.OPEN_FILE) return true;
        for (Text sibling : text.getSiblings()) if (containsFileLink(sibling)) return true;
        return false;
    }
}
