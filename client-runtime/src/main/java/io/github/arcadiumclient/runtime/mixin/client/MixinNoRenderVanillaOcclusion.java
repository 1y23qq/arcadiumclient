// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.Block;
import net.minecraft.client.render.world.RenderChunk;
import io.github.arcadiumclient.runtime.client.features.NoRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderChunk.class)
public abstract class MixinNoRenderVanillaOcclusion {
    @WrapOperation(method = "compile", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;isSolidRender()Z"))
    private boolean arcadium$ignoreHiddenLeafOccluders(Block block, Operation<Boolean> original) {
        return !NoRender.hideOpaqueLeaves(block) && original.call(block);
    }
}
