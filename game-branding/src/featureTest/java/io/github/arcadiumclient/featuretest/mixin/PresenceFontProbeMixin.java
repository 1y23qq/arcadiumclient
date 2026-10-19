package io.github.arcadiumclient.featuretest.mixin;

import io.github.arcadiumclient.featuretest.PresenceQa;
import net.minecraft.client.render.TextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TextRenderer.class, priority = 400)
public class PresenceFontProbeMixin {
    @Inject(method = "draw(Ljava/lang/String;FFIZ)I", at = @At("RETURN"))
    private void arcadium$presenceText(String text, float x, float y, int color, boolean shadow, CallbackInfoReturnable<Integer> ci) { PresenceQa.text(text, x, y); }
}
