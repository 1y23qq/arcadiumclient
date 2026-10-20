// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import io.github.arcadiumclient.runtime.client.features.NoRender;
import net.minecraft.block.Block;
import net.minecraft.client.render.block.BlockRenderer;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockRenderer.class, priority = 2000)
public abstract class MixinNoRenderBlocks {
    @Shadow public WorldView world;
    @Inject(method = "tesselateInWorld", at = @At("HEAD"), cancellable = true)
    private void arcadium$filterBlock(Block block, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        if (NoRender.hideBlock(block, x, y, z, world)) cir.setReturnValue(false);
    }
}
