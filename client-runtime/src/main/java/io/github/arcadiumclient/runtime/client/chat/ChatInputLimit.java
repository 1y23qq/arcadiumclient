// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.chat;

import java.nio.charset.StandardCharsets;
import org.polyfrost.oneconfig.internal.emoji.EmojiText;

public final class ChatInputLimit {
    public static final int SERVER_CHARACTERS = 100;
    public static final int STRING_BYTES = 32767;
    private ChatInputLimit() { }

    public static boolean localCommand(String text, boolean bypass) {
        return bypass && text.stripLeading().startsWith("/");
    }

    public static boolean fits(String text, boolean bypass) {
        if (!wellFormed(text)) return false;
        boolean local = localCommand(text, bypass);
        String wire = local ? text : EmojiText.toWire(text);
        return wire.length() <= (local ? STRING_BYTES : SERVER_CHARACTERS)
            && wire.getBytes(StandardCharsets.UTF_8).length <= STRING_BYTES;
    }

    public static String insertion(String text, int cursor, int selection, String value, boolean bypass) {
        int start = Math.max(0, Math.min(text.length(), Math.min(cursor, selection)));
        int end = Math.max(start, Math.min(text.length(), Math.max(cursor, selection)));
        String before = text.substring(0, start), after = text.substring(end);
        String candidate = before + value + after;
        if (fits(candidate, bypass)) return value;
        if (localCommand(candidate, bypass)) {
            int units = STRING_BYTES - before.length() - after.length();
            int bytes = STRING_BYTES - utf8Bytes(before) - utf8Bytes(after);
            int last = 0;
            for (int at = 0; at < value.length();) {
                int point = value.codePointAt(at), count = Character.charCount(point);
                if (Character.isSurrogate((char) point) && count == 1 || at + count > units) break;
                int used = point <= 0x7f ? 1 : point <= 0x7ff ? 2 : point <= 0xffff ? 3 : 4;
                if (used > bytes) break;
                at += count; bytes -= used; last = at;
            }
            String accepted = value.substring(0, last);
            if (fits(before + accepted + after, bypass)) return accepted;
        }
        int last = 0, available = Math.max(0, SERVER_CHARACTERS - before.length() - after.length());
        for (int at = 0; at < value.length();) {
            int point = value.codePointAt(at), count = Character.charCount(point);
            if (at + count > available) break;
            at += count;
            if (fits(before + value.substring(0, at) + after, bypass)) last = at;
        }
        return value.substring(0, last);
    }

    public static String text(String value, boolean bypass) { return insertion("", 0, 0, value, bypass); }

    public static int boundary(String text, int target, int previous) {
        int at = Math.max(0, Math.min(text.length(), target));
        if (at > 0 && at < text.length() && Character.isHighSurrogate(text.charAt(at - 1)) && Character.isLowSurrogate(text.charAt(at)))
            at += target < previous ? -1 : 1;
        return at;
    }

    private static int utf8Bytes(String value) { return value.getBytes(StandardCharsets.UTF_8).length; }

    private static boolean wellFormed(String value) {
        for (int at = 0; at < value.length(); at++) {
            char character = value.charAt(at);
            if (Character.isHighSurrogate(character)) {
                if (++at >= value.length() || !Character.isLowSurrogate(value.charAt(at))) return false;
            } else if (Character.isLowSurrogate(character)) return false;
        }
        return true;
    }
}
