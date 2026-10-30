// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.emoji;

import org.polyfrost.oneconfig.internal.emoji.EmojiText;

public final class EmojiLayout {
    private EmojiLayout() { }

    public static int advance(String formatting) {
        boolean bold = false;
        for (int index = 0; index + 1 < formatting.length(); index++) {
            if (formatting.charAt(index) != '\u00a7') continue;
            char code = Character.toLowerCase(formatting.charAt(++index));
            if (code == 'l') bold = true;
            else if (code == 'r' || code >= '0' && code <= '9' || code >= 'a' && code <= 'f') bold = false;
        }
        return EmojiText.ADVANCE + (bold ? 1 : 0);
    }
}
