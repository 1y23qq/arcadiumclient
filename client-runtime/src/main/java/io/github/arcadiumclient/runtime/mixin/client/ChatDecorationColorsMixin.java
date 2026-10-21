// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.client.chat.ChatTextMarks;
import io.github.arcadiumclient.runtime.client.features.ChatTweaksConfig;
import net.minecraft.client.render.TextRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = TextRenderer.class, priority = 1800)
public abstract class ChatDecorationColorsMixin {
    @WrapMethod(method = "draw(Ljava/lang/String;FFIZ)I")
    private int arcadium$decoratedColors(String text, float x, float y, int color, boolean shadow, Operation<Integer> original) {
        if (text == null || text.indexOf(ChatTextMarks.COUNTER) < 0 && text.indexOf(ChatTextMarks.TIMESTAMP) < 0)
            return original.call(ChatTextMarks.strip(text), x, y, color, shadow);
        int begin = 0, marker = 0;
        float cursor = x;
        for (int i = 0; i <= text.length(); i++) {
            if (i < text.length() && !ChatTextMarks.marker(text.charAt(i))) continue;
            String segment = text.substring(begin, i);
            String inherited = TextRenderer.isolateFormatting(ChatTextMarks.strip(text.substring(0, begin)));
            int paint = color;
            if (marker != 0) {
                java.awt.Color selected = marker == ChatTextMarks.COUNTER ? ChatTweaksConfig.getCompactChatColor() : ChatTweaksConfig.getTimestampsColor();
                int rgb = selected == null ? 0xAAAAAA : selected.getRGB();
                paint = ((color & 0xFC000000) == 0 ? 0xFF000000 : color & 0xFF000000) | rgb & 0xFFFFFF;
                inherited = "§r";
                segment = ChatTextMarks.withoutColors(segment);
            }
            int end = original.call(inherited + segment, cursor, y, paint, shadow);
            cursor = Math.max(cursor, end - (shadow ? 1 : 0));
            if (i < text.length()) marker = text.charAt(i) == ChatTextMarks.END ? 0 : text.charAt(i);
            begin = i + 1;
        }
        return (int) cursor + (shadow ? 1 : 0);
    }

    @WrapMethod(method = "getWidth(Ljava/lang/String;)I")
    private int arcadium$decorationWidth(String text, Operation<Integer> original) { return original.call(ChatTextMarks.strip(text)); }

    @WrapMethod(method = "getWidth(C)I")
    private int arcadium$decorationWidth(char value, Operation<Integer> original) { return ChatTextMarks.marker(value) ? 0 : original.call(value); }
}
