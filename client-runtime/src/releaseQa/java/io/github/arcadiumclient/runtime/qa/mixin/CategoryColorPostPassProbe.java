// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.CategoryColorNativeQa;
import net.minecraft.client.render.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PostPass.class)
public abstract class CategoryColorPostPassProbe {
    @Inject(method = "process(F)V", at = @At("HEAD"))
    private void categories$beforePass(float delta, CallbackInfo callback) throws Exception {
        if (CategoryColorNativeQa.enabled()) CategoryColorNativeQa.postPassStart((PostPass) (Object) this);
    }

    @Inject(method = "process(F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;end()V"))
    private void categories$beforeQuad(float delta, CallbackInfo callback) throws Exception {
        if (CategoryColorNativeQa.enabled()) CategoryColorNativeQa.postPassDraw((PostPass) (Object) this);
    }
}
