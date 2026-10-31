// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package org.polyfrost.oneconfig.internal.emoji;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EmojiText {
    public static final int FIRST_GLYPH = 0xF400;
    public static final int ADVANCE = 9;
    public static final String ATLAS = "/assets/oneconfig/emoji/twemoji-atlas.png";
    public record Entry(String name, int codepoint, char glyph) {
        public String shortcode() { return ":" + name + ":"; }
    }
    private static final String[] NAMES = {
        "grinning", "smile", "laughing", "joy", "rofl", "wink", "blush", "heart_eyes",
        "sunglasses", "thinking", "neutral_face", "expressionless", "unamused", "sweat", "pensive", "confused",
        "upside_down", "smirk", "cry", "sob", "angry", "rage", "scream", "skull",
        "clown", "poop", "ghost", "alien", "robot", "sleeping", "yawning", "facepalm",
        "thumbsup", "thumbsdown", "wave", "clap", "pray", "muscle", "ok_hand", "peace",
        "crossed_fingers", "handshake", "eyes", "heart", "broken_heart", "fire", "star", "sparkles",
        "100", "trophy", "medal", "crown", "diamond", "crossed_swords", "bow", "pick",
        "boom", "lightning", "snowflake", "sun", "check", "x", "warning", "controller"
    };
    private static final int[] CODEPOINTS = {
        0x1f600, 0x1f604, 0x1f606, 0x1f602, 0x1f923, 0x1f609, 0x1f60a, 0x1f60d,
        0x1f60e, 0x1f914, 0x1f610, 0x1f611, 0x1f612, 0x1f613, 0x1f614, 0x1f615,
        0x1f643, 0x1f60f, 0x1f622, 0x1f62d, 0x1f620, 0x1f621, 0x1f631, 0x1f480,
        0x1f921, 0x1f4a9, 0x1f47b, 0x1f47d, 0x1f916, 0x1f634, 0x1f971, 0x1f926,
        0x1f44d, 0x1f44e, 0x1f44b, 0x1f44f, 0x1f64f, 0x1f4aa, 0x1f44c, 0x270c,
        0x1f91e, 0x1f91d, 0x1f440, 0x2764, 0x1f494, 0x1f525, 0x2b50, 0x2728,
        0x1f4af, 0x1f3c6, 0x1f3c5, 0x1f451, 0x1f48e, 0x2694, 0x1f3f9, 0x26cf,
        0x1f4a5, 0x26a1, 0x2744, 0x2600, 0x2705, 0x274c, 0x26a0, 0x1f3ae
    };
    private static final class Node {
        final Map<Character, Node> children = new HashMap<>();
        int entry = -1;
    }
    private record Match(int entry, int end) { }
    private static final Node DISPLAY = new Node(), UNICODE = new Node();
    private static final List<Entry> ENTRIES;
    private static final Map<String, String> CACHE = new LinkedHashMap<>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> eldest) { return size() > 512; }
    };
    private static volatile boolean enabled = true;
    private static volatile boolean hideInScoreboard = true;
    private static final ThreadLocal<Boolean> SUPPRESSED = new ThreadLocal<>();

    static {
        ArrayList<Entry> entries = new ArrayList<>();
        for (int i = 0; i < NAMES.length; i++) {
            Entry entry = new Entry(NAMES[i], CODEPOINTS[i], (char) (FIRST_GLYPH + i));
            entries.add(entry);
            add(DISPLAY, entry.shortcode(), i);
            String unicode = new String(Character.toChars(entry.codepoint()));
            if (entry.codepoint() < 0x10000) unicode += "\ufe0f";
            add(DISPLAY, unicode, i);
            add(UNICODE, unicode, i);
            if (entry.codepoint() >= 0x10000) {
                add(DISPLAY, unicode + "\ufe0f", i);
                add(UNICODE, unicode + "\ufe0f", i);
            }
        }
        alias("+1", 32); alias("-1", 33); alias("red_heart", 43); alias("laugh", 2);
        alias("upside_down_face", 16); alias("thinking_face", 9); alias("ok", 38);
        alias("white_check_mark", 60); alias("heavy_check_mark", 60); alias("video_game", 63);
        ENTRIES = List.copyOf(entries);
    }
    private EmojiText() { }
    private static void alias(String name, int index) { add(DISPLAY, ":" + name + ":", index); }
    private static void add(Node root, String token, int entry) {
        Node node = root;
        for (int i = 0; i < token.length(); i++) node = node.children.computeIfAbsent(token.charAt(i), ignored -> new Node());
        node.entry = entry;
    }
    private static Match match(Node root, String text, int start) {
        Node node = root;
        int end = start, found = -1;
        for (int cursor = start; cursor < text.length(); cursor++) {
            node = node.children.get(text.charAt(cursor));
            if (node == null) break;
            if (node.entry >= 0) { found = node.entry; end = cursor + 1; }
        }
        return found < 0 ? null : new Match(found, end);
    }
    public static List<Entry> entries() { return ENTRIES; }
    public static boolean enabled() { return enabled && SUPPRESSED.get() == null; }
    public static void setEnabled(boolean value) { enabled = value; }
    public static void setHideInScoreboard(boolean value) { hideInScoreboard = value; }
    public static boolean beginScoreboard() {
        boolean previous = SUPPRESSED.get() != null;
        if (hideInScoreboard) SUPPRESSED.set(Boolean.TRUE);
        return previous;
    }
    public static void endScoreboard(boolean previous) {
        if (previous) SUPPRESSED.set(Boolean.TRUE); else SUPPRESSED.remove();
    }
    public static boolean isGlyph(int codepoint) { return codepoint >= FIRST_GLYPH && codepoint < FIRST_GLYPH + ENTRIES.size(); }
    public static int index(int codepoint) { return isGlyph(codepoint) ? codepoint - FIRST_GLYPH : -1; }

    public static String display(String text) {
        if (!enabled() || text == null || text.isEmpty()) return text;
        synchronized (CACHE) {
            String cached = CACHE.get(text);
            if (cached != null) return cached;
        }
        StringBuilder result = null;
        int copied = 0;
        for (int cursor = 0; cursor < text.length();) {
            Match match = match(DISPLAY, text, cursor);
            if (match == null) { cursor++; continue; }
            if (result == null) result = new StringBuilder(text.length());
            result.append(text, copied, cursor).append(ENTRIES.get(match.entry).glyph());
            cursor = copied = match.end;
        }
        String output = result == null ? text : result.append(text, copied, text.length()).toString();
        if (text.length() <= 2048) synchronized (CACHE) { CACHE.put(text, output); }
        return output;
    }

    public static String toWire(String text) {
        if (!enabled || text == null || text.isEmpty() || text.startsWith("/")) return text;
        StringBuilder result = null;
        int copied = 0;
        for (int cursor = 0; cursor < text.length();) {
            int entry = index(text.charAt(cursor));
            Match match = entry >= 0 ? new Match(entry, cursor + 1) : match(UNICODE, text, cursor);
            if (match == null) { cursor++; continue; }
            if (result == null) result = new StringBuilder(text.length());
            result.append(text, copied, cursor).append(ENTRIES.get(match.entry).shortcode());
            cursor = copied = match.end;
        }
        return result == null ? text : result.append(text, copied, text.length()).toString();
    }

    public static String sourceTrim(String source, String encoded, String trimmed, boolean reverse) {
        if (source == null || source == encoded || source.equals(encoded)) return trimmed;
        int cutoff = reverse ? encoded.length() - trimmed.length() : trimmed.length();
        int cursor = 0, output = 0;
        while (cursor < source.length() && output < cutoff) {
            Match match = match(DISPLAY, source, cursor);
            cursor = match == null ? cursor + 1 : match.end;
            output++;
        }
        return reverse ? source.substring(cursor) : source.substring(0, cursor);
    }
}
