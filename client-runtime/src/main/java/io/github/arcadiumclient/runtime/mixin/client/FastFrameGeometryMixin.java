// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client;

import io.github.arcadiumclient.runtime.client.optimizations.FastFramePresentation;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.platform.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderTarget.class)
public abstract class FastFrameGeometryMixin {
    @Shadow public abstract void unbindRead();

    @Inject(method = "draw(IIZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/BufferBuilder;begin(ILnet/minecraft/client/render/vertex/VertexFormat;)V"), cancellable = true)
    private void arcadium$cachedFrameQuad(int width, int height, boolean resetBlend, CallbackInfo callback) {
        if (!FastFramePresentation.draw((RenderTarget) (Object) this, width, height, resetBlend)) return;
        unbindRead();
        GlStateManager.depthMask(true);
        GlStateManager.colorMask(true, true, true, true);
        callback.cancel();
    }
}
