// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.client.optimizations.GpuNameTags;
import io.github.arcadiumclient.runtime.client.optimizations.TagGeometryCache;
import io.github.arcadiumclient.runtime.qa.RetainedSceneQa;
import net.minecraft.client.render.vertex.BufferBuilder;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=GpuNameTags.class,remap=false)
public abstract class RetainedPacketProbe {
    @Inject(method="queueRetained",at=@At("HEAD"),cancellable=true)
    private void qa$capture(BufferBuilder pass,TagGeometryCache.Mesh mesh,int alpha,Matrix4fc matrix,float u,float v,CallbackInfo callback){
        if(RetainedSceneQa.enabled()){RetainedSceneQa.queued(pass,mesh,alpha,matrix,u,v);callback.cancel();}
    }
}
