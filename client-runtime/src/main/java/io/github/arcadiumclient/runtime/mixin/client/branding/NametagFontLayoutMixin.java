// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client.branding;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.client.branding.NametagLayout;
import io.github.arcadiumclient.runtime.client.branding.PlayerBadgeRenderer;
import net.minecraft.client.render.TextRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = TextRenderer.class, priority = 1900)
public abstract class NametagFontLayoutMixin {
    @WrapMethod(method = "getWidth(Ljava/lang/String;)I")
    private int arcadium$nametagWidth(String text, Operation<Integer> original) {
        return original.call(NametagLayout.display(text)) + (NametagLayout.badge(text) ? PlayerBadgeRenderer.ADVANCE : 0);
    }

    @WrapMethod(method = "draw(Ljava/lang/String;FFIZ)I")
    private int arcadium$nametagText(String text, float x, float y, int color, boolean shadow, Operation<Integer> original) {
        if (NametagLayout.badge(text)) {
            PlayerBadgeRenderer.draw(x, y, color);
            x += PlayerBadgeRenderer.ADVANCE;
        }
        return original.call(NametagLayout.display(text), x, y, color, shadow);
    }
}
