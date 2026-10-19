// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldView;
import io.github.arcadiumclient.runtime.client.features.NoRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@IfModLoaded("argentum")
@Mixin(targets = "dev.rdh.argentum.impl.render.terrain.compile.pipeline.FastBlockRenderer", remap = false, priority = 2000)
public abstract class MixinNoRenderFastBlocks {
    @Shadow @Final private BlockPos.Mutable neighborPos;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false)
    private void arcadium$filterFastBlock(BlockState state, BlockPos pos, @Coerce Object world, @Coerce Object model,
                                         @Coerce Object buffers, @Coerce Object section, CallbackInfo ci) {
        if (NoRender.hideBlock(state, pos, (WorldView) world)) ci.cancel();
    }

    @Inject(method = "isHiddenByNeighbor", at = @At("HEAD"), cancellable = true, remap = false)
    private void arcadium$revealHiddenLeafNeighbor(@Coerce Object model, BlockPos pos, @Coerce Object world,
                                                  Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (NoRender.hidesLeaves() && NoRender.hideOpaqueLeaves(((WorldView) world).getBlockState(neighborPos).getBlock())) {
            cir.setReturnValue(false);
        }
    }
}
