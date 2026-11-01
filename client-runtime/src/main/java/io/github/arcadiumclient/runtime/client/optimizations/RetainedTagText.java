// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import io.github.arcadiumclient.runtime.client.bedwars.ArcadiumPrefix;
import java.util.LinkedHashMap;
import java.util.Map;
import org.polyfrost.oneconfig.internal.emoji.EmojiText;

public final class RetainedTagText {
    public enum Eligibility { NATIVE, EMOJI, OBFUSCATED, ANIMATED, EMOJI_DISABLED, UNSUPPORTED }
    private record Cached(boolean emojiEnabled, Eligibility eligibility) { }
    private static final Map<String, Cached> CACHE = new LinkedHashMap<>(256, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Cached> entry) { return size() > 512; }
    };

    private RetainedTagText() { }

    public static Eligibility classify(String text) {
        if (text == null || text.length() > 2048) return Eligibility.UNSUPPORTED;
        boolean enabled = EmojiText.enabled();
        Cached cached = CACHE.get(text);
        if (cached != null && cached.emojiEnabled == enabled) return cached.eligibility;
        Eligibility result = inspect(text, enabled);
        CACHE.put(text, new Cached(enabled, result));
        return result;
    }

    private static Eligibility inspect(String text, boolean emojiEnabled) {
        boolean special = false;
        for (int i = 0; i < text.length(); i++) {
            char glyph = text.charAt(i);
            if (glyph == ArcadiumPrefix.START || glyph == ArcadiumPrefix.END) return Eligibility.ANIMATED;
            if (glyph == '§' && i + 1 < text.length() && (text.charAt(i + 1) == 'k' || text.charAt(i + 1) == 'K')) return Eligibility.OBFUSCATED;
            if (EmojiText.isGlyph(glyph)) return Eligibility.EMOJI;
            special |= glyph == ':' || glyph >= 0x2000;
            if (Character.isHighSurrogate(glyph)) {
                if (i + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(++i))) return Eligibility.UNSUPPORTED;
            } else if (Character.isLowSurrogate(glyph)) return Eligibility.UNSUPPORTED;
        }
        if (!special) return Eligibility.NATIVE;
        if (!emojiEnabled) return Eligibility.EMOJI_DISABLED;
        return text.equals(EmojiText.display(text)) ? Eligibility.NATIVE : Eligibility.EMOJI;
    }
}
