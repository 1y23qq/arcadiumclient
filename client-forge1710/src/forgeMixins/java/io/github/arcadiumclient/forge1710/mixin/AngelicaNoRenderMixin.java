// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.arcadiumclient.runtime.client.features.NoRender;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.gtnewhorizons.angelica.rendering.celeritas.AngelicaChunkBuilderMeshingTask")
public abstract class AngelicaNoRenderMixin {
    @Inject(method = "renderBlock", at = @At("HEAD"), cancellable = true, remap = false)
    private void arcadium$block(CallbackInfo callback, @Local(argsOnly = true) Block block,
            @Local(argsOnly = true, ordinal = 0) int x, @Local(argsOnly = true, ordinal = 1) int y,
            @Local(argsOnly = true, ordinal = 2) int z, @Local(argsOnly = true) RenderBlocks renderer) {
        if (NoRender.hideBlock(block, x, y, z, renderer.field_147845_a)) callback.cancel();
    }
}
