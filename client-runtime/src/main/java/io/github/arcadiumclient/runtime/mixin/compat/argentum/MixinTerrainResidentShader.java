// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.TerrainResidentShaderFacts;
import io.github.arcadiumclient.runtime.client.optimizations.GpuTerrainCommands;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.gl.shader.ShaderConstants;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderInterface;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@IfModLoaded("argentum")
@Mixin(targets="org.embeddedt.embeddium.impl.render.chunk.ShaderChunkRenderer",remap=false)
public abstract class MixinTerrainResidentShader {
    @WrapMethod(method="createShader",remap=false)
    private GlProgram<ChunkShaderInterface> terrain072$compileFacts(String name,ChunkShaderOptions options,Operation<GlProgram<ChunkShaderInterface>> original){
        if(!GpuTerrainCommands.residentBootRequested())return original.call(name,options);
        var token=TerrainResidentShaderFacts.begin();GlProgram<ChunkShaderInterface> program=null;
        try{program=original.call(name,options);return program;}finally{TerrainResidentShaderFacts.finish(token,program);}
    }
    @ModifyExpressionValue(method="createShader",at=@At(value="INVOKE",target="Lorg/embeddedt/embeddium/impl/render/chunk/shader/ChunkShaderOptions;constants()Lorg/embeddedt/embeddium/impl/gl/shader/ShaderConstants;"),remap=false)
    private ShaderConstants terrain072$actualConstants(ShaderConstants constants){if(GpuTerrainCommands.residentBootRequested())TerrainResidentShaderFacts.constants(constants);return constants;}
}
