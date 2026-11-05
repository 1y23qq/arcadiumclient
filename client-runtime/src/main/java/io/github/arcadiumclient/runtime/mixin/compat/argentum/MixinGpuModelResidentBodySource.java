// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@IfModLoaded("argentum")
@Mixin(targets="org.embeddedt.embeddium.impl.render.shader.ShaderLoader",remap=false)
public abstract class MixinGpuModelResidentBodySource {
    @Shadow public static String getShaderSource(String name){throw new AssertionError();}
    @Inject(method="getShaderSource",at=@At("RETURN"),cancellable=true,remap=false)
    private static void arcadium$bodyShader(String name,CallbackInfoReturnable<String> callback){
        if(!GpuResidentBodyShaders.enabled()||ModernArmorGlintPipeline.shaderAdapterEnabled())return;
        if(!name.equals("argentum:entity_instancing.vert"))return;
        try{if(GpuResidentBodyShaderState.supported())callback.setReturnValue(GpuResidentBodyShaders.patch(name,callback.getReturnValue(),getShaderSource("argentum:entity_instancing.frag")));}
        catch(IllegalStateException noContext){}
    }
}
