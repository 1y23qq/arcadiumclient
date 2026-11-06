// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.GpuTerrainCommands;
import io.github.arcadiumclient.runtime.client.optimizations.TerrainResidentShaderFacts;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@IfModLoaded("argentum")
@Mixin(targets="org.embeddedt.embeddium.impl.gl.GlObject",remap=false)
public abstract class MixinTerrainResidentShaderDelete {
    @Inject(method="delete",at=@At("HEAD"),remap=false)
    private void terrain072$programDeleted(CallbackInfo callback){
        if(GpuTerrainCommands.residentBootRequested()&&(Object)this instanceof GlProgram<?>)TerrainResidentShaderFacts.deleted(this);
    }
}
