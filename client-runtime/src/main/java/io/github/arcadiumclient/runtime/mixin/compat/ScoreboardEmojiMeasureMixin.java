// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.TextRenderer;
import org.polyfrost.oneconfig.internal.emoji.EmojiText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "org.polyfrost.vanillahud.hud.ScoreboardHud", remap = false)
public class ScoreboardEmojiMeasureMixin {
    @WrapOperation(method = "measureSize", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/TextRenderer;getWidth(Ljava/lang/String;)I", remap = true))
    private int arcadium$width(TextRenderer font, String text, Operation<Integer> original) {
        boolean previous = EmojiText.beginScoreboard();
        try { return original.call(font, text); } finally { EmojiText.endScoreboard(previous); }
    }
}
