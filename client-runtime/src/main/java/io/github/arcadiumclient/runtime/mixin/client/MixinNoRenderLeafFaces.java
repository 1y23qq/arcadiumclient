// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldView;
import io.github.arcadiumclient.runtime.client.features.NoRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public abstract class MixinNoRenderLeafFaces {
    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true)
    private void arcadium$revealFacesBehindLeaves(WorldView world, BlockPos neighbor, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (NoRender.hidesLeaves() && NoRender.hideOpaqueLeaves(world.getBlockState(neighbor).getBlock())) cir.setReturnValue(true);
    }
}
