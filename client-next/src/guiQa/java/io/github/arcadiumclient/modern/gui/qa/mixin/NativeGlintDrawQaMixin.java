// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.gui.qa.mixin;

import io.github.arcadiumclient.modern.gui.qa.NativeRuntimeProbe;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PreparedRenderType.class)
public abstract class NativeGlintDrawQaMixin {
    @Inject(method = "drawFromBuffer(Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/IndexType;III)V", at = @At("RETURN"))
    private void arcadium$nativeDraw(CallbackInfo ci) {
        NativeRuntimeProbe.glintDraw(((PreparedRenderType) (Object) this).pipeline().getLocation().toString());
    }
}
