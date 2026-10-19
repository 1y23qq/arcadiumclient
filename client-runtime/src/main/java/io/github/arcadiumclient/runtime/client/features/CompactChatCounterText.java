// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features;

import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

public final class CompactChatCounterText extends LiteralText {
    public CompactChatCounterText(String value) {
        super(value);
    }

    @Override
    public CompactChatCounterText copy() {
        CompactChatCounterText copy = new CompactChatCounterText(getRawString());
        copy.setStyle(getStyle().deepCopy());
        for (Text sibling : getSiblings()) copy.append(sibling.copy());
        return copy;
    }
}
