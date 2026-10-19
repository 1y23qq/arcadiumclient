// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.render.TextRenderer;
import io.github.arcadiumclient.runtime.client.bedwars.ArcadiumPrefix;
import io.github.arcadiumclient.runtime.client.bedwars.BedwarsOverlayConfig;
import org.polyfrost.oneconfig.internal.ui.themes.ThemeAccent;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = TextRenderer.class, priority = 1700)
public abstract class MixinArcadiumPrefixTextRenderer {
    @WrapMethod(method = "draw(Ljava/lang/String;FFIZ)I")
    private int arcadium$drawPrefix(String text, float x, float y, int color, boolean shadow, Operation<Integer> original) {
        if (text == null) return original.call(null, x, y, color, shadow);
        int start = text.indexOf(ArcadiumPrefix.MARKED);
        if (start < 0) return original.call(ArcadiumPrefix.plainMarkers(text), x, y, color, shadow);
        TextRenderer renderer = (TextRenderer) (Object) this;
        String before = text.substring(0, start);
        original.call(before, x, y, color, shadow);
        float cursor = x + renderer.getWidth(before);
        BedwarsOverlayConfig config = BedwarsOverlayConfig.INSTANCE;
        long now = System.nanoTime();
        ThemeAccent.Snapshot accent = ThemeAccent.snapshot();
        double phase = ThemeAccent.phaseAt(now);
        String name = "Arcadium";
        for (int i = 0; i < name.length(); i++) {
            String letter = "§r" + name.charAt(i);
            int rgb = accent.colorAtPhase(config.animatedPrefix ? i / 16.0 : 0, phase, config.animatedPrefix);
            int alpha = color & 0xFF000000;
            original.call(letter, cursor, y, (alpha == 0 ? 0xFF000000 : alpha) | (rgb & 0xFFFFFF), shadow);
            cursor += renderer.getWidth(letter);
        }
        String after = ArcadiumPrefix.plainMarkers(text.substring(start + ArcadiumPrefix.MARKED.length()));
        return original.call(after, cursor, y, color, shadow);
    }

    @WrapMethod(method = "getWidth(Ljava/lang/String;)I")
    private int arcadium$prefixWidth(String text, Operation<Integer> original) {
        return original.call(ArcadiumPrefix.plainMarkers(text));
    }

    @WrapMethod(method = "getWidth(C)I")
    private int arcadium$markerWidth(char value, Operation<Integer> original) {
        return value == ArcadiumPrefix.START || value == ArcadiumPrefix.END ? 0 : original.call(value);
    }
}
