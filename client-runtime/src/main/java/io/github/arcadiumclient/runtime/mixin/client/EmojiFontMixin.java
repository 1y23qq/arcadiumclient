// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.client.emoji.EmojiPainter;
import java.util.List;
import net.minecraft.client.render.TextRenderer;
import org.polyfrost.oneconfig.internal.emoji.EmojiText;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = TextRenderer.class, priority = 1500)
public abstract class EmojiFontMixin {
    @WrapMethod(method = "draw(Ljava/lang/String;FFIZ)I")
    private int arcadium$emojiRuns(String text, float x, float y, int color, boolean shadow, Operation<Integer> original) {
        String display = EmojiText.display(text);
        if (display == null || !EmojiText.enabled()) return original.call(display, x, y, color, shadow);
        TextRenderer font = (TextRenderer) (Object) this;
        float cursor = x;
        int start = 0;
        for (int index = 0; index < display.length(); index++) {
            char glyph = display.charAt(index);
            if (!EmojiText.isGlyph(glyph)) continue;
            String inherited = TextRenderer.isolateFormatting(display.substring(0, start));
            String run = inherited + display.substring(start, index);
            if (!run.isEmpty()) original.call(run, cursor, y, color, shadow);
            cursor += font.getWidth(run);
            EmojiPainter.draw(glyph, cursor, y, 8f, color, shadow);
            String style = TextRenderer.isolateFormatting(display.substring(0, index));
            cursor += font.getWidth(style + glyph);
            start = index + 1;
        }
        if (start == 0) return original.call(display, x, y, color, shadow);
        if (start < display.length()) {
            String rest = TextRenderer.isolateFormatting(display.substring(0, start)) + display.substring(start);
            return original.call(rest, cursor, y, color, shadow);
        }
        return (int) cursor + (shadow ? 1 : 0);
    }

    @WrapMethod(method = "getWidth(Ljava/lang/String;)I")
    private int arcadium$emojiWidth(String text, Operation<Integer> original) {
        String display = EmojiText.display(text);
        if (display == null || !EmojiText.enabled()) return original.call(display);
        int width = 0, start = 0;
        for (int index = 0; index < display.length(); index++) {
            if (!EmojiText.isGlyph(display.charAt(index))) continue;
            String inherited = TextRenderer.isolateFormatting(display.substring(0, start));
            width += original.call(inherited + display.substring(start, index));
            String style = TextRenderer.isolateFormatting(display.substring(0, index));
            width += EmojiText.ADVANCE + (style.contains("§l") ? 1 : 0);
            start = index + 1;
        }
        return start == 0 ? original.call(display)
            : width + original.call(TextRenderer.isolateFormatting(display.substring(0, start)) + display.substring(start));
    }

    @WrapMethod(method = "getWidth(C)I")
    private int arcadium$emojiWidth(char glyph, Operation<Integer> original) {
        return EmojiText.enabled() && EmojiText.isGlyph(glyph) ? EmojiText.ADVANCE : original.call(glyph);
    }

    @WrapMethod(method = "trim(Ljava/lang/String;I)Ljava/lang/String;")
    private String arcadium$emojiTrim(String text, int width, Operation<String> original) {
        String encoded = EmojiText.display(text);
        return EmojiText.sourceTrim(text, encoded, original.call(encoded, width), false);
    }

    @WrapMethod(method = "trim(Ljava/lang/String;IZ)Ljava/lang/String;")
    private String arcadium$emojiTrim(String text, int width, boolean reverse, Operation<String> original) {
        String encoded = EmojiText.display(text);
        return EmojiText.sourceTrim(text, encoded, original.call(encoded, width, reverse), reverse);
    }

    @WrapMethod(method = "split(Ljava/lang/String;I)Ljava/util/List;")
    private List<String> arcadium$emojiSplit(String text, int width, Operation<List<String>> original) {
        return original.call(EmojiText.display(text), width);
    }
}
