// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import io.github.arcadiumclient.modern.client.features.NoRender;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelBlockRenderer.class)
public abstract class NoRenderBlocksMixin {
    @Inject(method = "tesselateBlock", at = @At("HEAD"), cancellable = true)
    private void arcadium$block(BlockQuadOutput output, float x, float y, float z, BlockAndTintGetter level,
            BlockPos position, BlockState state, BlockStateModel model, long seed, CallbackInfo ci) {
        if (NoRender.block(state)) ci.cancel();
    }
}
