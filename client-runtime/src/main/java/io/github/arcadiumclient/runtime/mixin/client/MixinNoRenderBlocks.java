// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import net.minecraft.block.state.BlockState;
import net.minecraft.client.render.block.BlockRenderDispatcher;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import io.github.arcadiumclient.runtime.client.features.NoRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockRenderDispatcher.class, priority = 2000)
public abstract class MixinNoRenderBlocks {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void arcadium$filterBlock(BlockState state, BlockPos pos, WorldView world, BufferBuilder buffer, CallbackInfoReturnable<Boolean> cir) {
        if (NoRender.hideBlock(state, pos, world)) cir.setReturnValue(false);
    }
}
