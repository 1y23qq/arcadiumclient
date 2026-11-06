// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.ModernGlint072Qa;
import io.github.arcadiumclient.runtime.qa.ModernGlintPixelTrace072Qa;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.model.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ModelPart.class, priority = 2000)
public abstract class ModernGlint072NativePartProbe {
    @org.spongepowered.asm.mixin.injection.Inject(method="render",at=@At("HEAD"))
    private void modern$nativeEntry(float scale,org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback){ModernGlint072Qa.nativeModelRoot((ModelPart)(Object)this);}
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;callList(I)V"))
    private void modern$nativeMatrix(int list, Operation<Void> original) {
        ModelPart part = (ModelPart)(Object)this;
        ModernGlint072Qa.nativeModelView(part);
        int token = ModernGlintPixelTrace072Qa.beforeNative(part, list);
        try { original.call(list); } finally { ModernGlintPixelTrace072Qa.after(token); }
    }
}
