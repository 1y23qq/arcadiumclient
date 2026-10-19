// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import net.minecraft.block.Block;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.SectionVisibilityBuilder;
import io.github.arcadiumclient.runtime.client.features.NoRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@IfModLoaded("argentum")
@Mixin(targets = "dev.rdh.argentum.impl.render.terrain.compile.task.ChunkBuilderMeshingTask", remap = false)
public abstract class MixinNoRenderSectionOcclusion {
    @WrapWithCondition(method = "execute", at = @At(value = "INVOKE", target = "Lorg/embeddedt/embeddium/impl/render/chunk/occlusion/SectionVisibilityBuilder;markOpaque(III)V"), remap = false)
    private boolean arcadium$ignoreHiddenLeafOccluder(SectionVisibilityBuilder builder, int x, int y, int z, @Local Block block) {
        return !NoRender.hideOpaqueLeaves(block);
    }
}
