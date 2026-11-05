// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.GpuVoxelSkinUploadFacts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@IfModLoaded("argentum")
@Mixin(targets="dev.rdh.argentum.api.render.instancing.TextureArrayManager$Pool",remap=false)
public abstract class MixinGpuVoxelSkinCopyFacts {
    @Inject(method="copy",at=@At("RETURN"),remap=false)
    private void arcadium$copied(int source,int index,CallbackInfoReturnable<Boolean> callback){GpuVoxelSkinUploadFacts.copied(this,source,index,callback.getReturnValueZ());}
}
