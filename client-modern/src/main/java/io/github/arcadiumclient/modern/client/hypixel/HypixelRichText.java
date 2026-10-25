// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.hypixel;

import java.util.Optional;
import java.util.function.UnaryOperator;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public final class HypixelRichText {
    private HypixelRichText() { }
    public static Component restyle(Component source, int from, int to, UnaryOperator<Style> transform) {
        MutableComponent result = Component.empty();
        int[] cursor = {0};
        source.visit((style, text) -> {
            int start = cursor[0], end = start + text.length();
            cursor[0] = end;
            int left = Math.max(start, from), right = Math.min(end, to);
            if (left >= right) result.append(Component.literal(text).setStyle(style));
            else {
                if (left > start) result.append(Component.literal(text.substring(0, left - start)).setStyle(style));
                result.append(Component.literal(text.substring(left - start, right - start)).setStyle(transform.apply(style)));
                if (right < end) result.append(Component.literal(text.substring(right - start)).setStyle(style));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }
    public static Component replace(Component source, int from, int to, String replacement) {
        MutableComponent result = Component.empty();
        int[] cursor = {0};
        boolean[] inserted = {false};
        source.visit((style, text) -> {
            int start = cursor[0], end = start + text.length(); cursor[0] = end;
            if (end <= from || start >= to) result.append(Component.literal(text).setStyle(style));
            else {
                int left = Math.max(start, from), right = Math.min(end, to);
                if (left > start) result.append(Component.literal(text.substring(0, left - start)).setStyle(style));
                if (!inserted[0]) { result.append(Component.literal(replacement).setStyle(style)); inserted[0] = true; }
                if (right < end) result.append(Component.literal(text.substring(right - start)).setStyle(style));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }
    public static Component slice(Component source, int from, int to) {
        MutableComponent result = Component.empty();
        int[] cursor = {0};
        source.visit((style, text) -> {
            int start = cursor[0], end = start + text.length(); cursor[0] = end;
            int left = Math.max(start, from), right = Math.min(end, to);
            if (left < right) result.append(Component.literal(text.substring(left - start, right - start)).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }
}
