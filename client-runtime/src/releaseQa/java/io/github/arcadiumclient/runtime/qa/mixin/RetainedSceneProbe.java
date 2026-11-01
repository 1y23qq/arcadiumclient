// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.arcadiumclient.runtime.client.optimizations.RetainedNameTags;
import io.github.arcadiumclient.runtime.client.optimizations.TagGeometryCache;
import io.github.arcadiumclient.runtime.qa.RetainedSceneQa;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=RetainedNameTags.class,remap=false)
public abstract class RetainedSceneProbe {
    @ModifyReturnValue(method="begin",at=@At("RETURN"))
    private static boolean qa$original(boolean reused){return RetainedSceneQa.prepareOriginal(reused);}
    @Inject(method="packet",at=@At("HEAD"))
    private static void qa$check(Object pass,TagGeometryCache.Mesh mesh,Matrix4fc matrix,float x,float y,int alpha,float u,float v,CallbackInfo callback){RetainedSceneQa.original(pass,mesh,matrix,x,y,alpha,u,v);}
    @Inject(method="end",at=@At("HEAD"))
    private static void qa$finish(CallbackInfo callback){RetainedSceneQa.end();}
}
