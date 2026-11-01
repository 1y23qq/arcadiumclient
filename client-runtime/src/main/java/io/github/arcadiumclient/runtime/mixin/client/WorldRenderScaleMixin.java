// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.arcadiumclient.runtime.client.optimizations.WorldRenderScale;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.PostChain;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public abstract class WorldRenderScaleMixin {
    @Shadow private PostChain shaderEffect;
    @Shadow public static boolean anaglyphEnabled;

    @WrapMethod(method = "renderWorld(FJ)V")
    private void arcadium$scaledWorld(float delta, long deadline, Operation<Void> original) {
        boolean scaled = WorldRenderScale.begin(shaderEffect != null || anaglyphEnabled);
        try { original.call(delta, deadline); }
        finally { if (scaled) WorldRenderScale.end(); }
    }

    @WrapOperation(method = "render(IFJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;viewport(IIII)V"))
    private void arcadium$worldViewport(int x, int y, int width, int height, Operation<Void> original) {
        WorldRenderScale.viewport(x, y, width, height);
    }
}
