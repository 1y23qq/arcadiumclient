package io.github.arcadiumclient.featuretest.mixin;
import io.github.arcadiumclient.featuretest.FeatureQa;
import io.github.arcadiumclient.featuretest.FeatureQa028;

import net.minecraft.client.render.TextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TextRenderer.class, priority = 400)
public abstract class FontProbeMixin {
    @Inject(method = "drawLayer(Ljava/lang/String;Z)V", at = @At("HEAD"))
    private void arcadium$displayedText(String text, boolean shadow, CallbackInfo callback) { FeatureQa.text(text); }

    @Inject(method = "draw(Ljava/lang/String;FFIZ)I", at = @At("RETURN"))
    private void arcadium$batchedText(String text, float x, float y, int color, boolean shadow, CallbackInfoReturnable<Integer> callback) { FeatureQa.text(text); FeatureQa028.text(text, x, y, color); }
}
