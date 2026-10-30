package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.client.emoji.EmojiPainter;
import io.github.arcadiumclient.runtime.qa.Chat059Qa;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EmojiPainter.class, remap = false)
public class Chat059EmojiProbe {
    @Inject(method = "draw", at = @At("HEAD"))
    private static void release$emoji(int glyph, float x, float y, float size, int color, boolean shadow, CallbackInfo ci) {
        if (Chat059Qa.measuring) Chat059Qa.positions.add(x);
    }
}
