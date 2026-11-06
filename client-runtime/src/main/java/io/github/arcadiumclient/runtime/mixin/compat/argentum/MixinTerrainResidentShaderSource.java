// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.TerrainResidentShaderFacts;
import io.github.arcadiumclient.runtime.client.optimizations.GpuTerrainCommands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@IfModLoaded("argentum")
@Mixin(targets="org.embeddedt.embeddium.impl.render.shader.ShaderLoader",remap=false)
public abstract class MixinTerrainResidentShaderSource {
    @WrapMethod(method="getShaderSource",remap=false)
    private static String terrain072$sourceFacts(String name,Operation<String> original){
        String source=original.call(name);
        if(GpuTerrainCommands.residentBootRequested()&&TerrainResidentShaderFacts.activeDepth()!=0)TerrainResidentShaderFacts.source(name,source);
        return source;
    }
}
