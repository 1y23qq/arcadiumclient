package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.Scoreboard060Qa;
import net.minecraft.client.render.TextRenderer;
import org.polyfrost.oneconfig.internal.emoji.EmojiText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TextRenderer.class)
public class Scoreboard060WidthProbe {
    @Inject(method = "getWidth(Ljava/lang/String;)I", at = @At("HEAD"))
    private void release$width(String text, CallbackInfoReturnable<Integer> ci) {
        if (Scoreboard060Qa.measureProbe) Scoreboard060Qa.widths.add(EmojiText.enabled());
    }
}
