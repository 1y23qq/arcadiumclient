// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import net.minecraft.client.render.TextRenderer;
import org.polyfrost.oneconfig.internal.emoji.EmojiText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TextRenderer.class, priority = 1500)
public abstract class EmojiFineWidthMixin {
    @Inject(method = "getCharWidthFine(C)F", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void arcadium$emojiFineWidth(char glyph, CallbackInfoReturnable<Float> callback) {
        if (EmojiText.enabled() && EmojiText.isGlyph(glyph)) callback.setReturnValue((float) EmojiText.ADVANCE);
    }
}
