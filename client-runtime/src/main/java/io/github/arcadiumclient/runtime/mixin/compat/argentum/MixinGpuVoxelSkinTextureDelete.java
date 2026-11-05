// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import io.github.arcadiumclient.runtime.client.optimizations.GpuVoxelSkinUploadFacts;
import net.minecraft.client.render.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractTexture.class)
public abstract class MixinGpuVoxelSkinTextureDelete {
    @Inject(method="clearGlId",at=@At("HEAD"))
    private void arcadium$clearUploadFact(CallbackInfo callback){GpuVoxelSkinUploadFacts.invalidate(this);}
}
