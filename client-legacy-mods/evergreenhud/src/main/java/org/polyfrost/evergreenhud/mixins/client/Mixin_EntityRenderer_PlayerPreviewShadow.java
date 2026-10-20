package org.polyfrost.evergreenhud.mixins.client;

//? if = 1.7.10 {
import net.minecraft.client.render.entity.EntityRenderer;
import org.polyfrost.evergreenhud.client.hooks.PlayerPreviewOffscreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class Mixin_EntityRenderer_PlayerPreviewShadow {
    @Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true)
    private void evergreenhud$hidePreviewShadow(CallbackInfo callback) {
        if (PlayerPreviewOffscreen.rendering) callback.cancel();
    }
}
//?}
