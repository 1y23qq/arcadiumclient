// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features;

import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

public final class CompactChatCounterDisplay {
    private CompactChatCounterDisplay() {}

    public static Text display(Text text) {
        return display(text, hidden());
    }

    public static Text display(Text text, boolean hidden) {
        if (text == null || !hidden || !containsCounter(text)) return text;
        if (text instanceof CompactChatCounterText) return new LiteralText("").setStyle(text.getStyle().deepCopy());
        Text copy = text.copy();
        removeCounters(copy);
        return copy;
    }

    private static boolean containsCounter(Text text) {
        if (text instanceof CompactChatCounterText) return true;
        for (Text sibling : text.getSiblings()) if (containsCounter(sibling)) return true;
        return false;
    }

    private static void removeCounters(Text text) {
        var iterator = text.getSiblings().iterator();
        while (iterator.hasNext()) {
            Text sibling = iterator.next();
            if (sibling instanceof CompactChatCounterText) iterator.remove();
            else removeCounters(sibling);
        }
    }

    private static boolean hidden() {
        return ChatTweaksConfig.getHideDuplicateCount();
    }
}
