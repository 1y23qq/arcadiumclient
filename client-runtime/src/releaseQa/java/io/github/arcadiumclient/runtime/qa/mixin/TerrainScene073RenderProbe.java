// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.TerrainScene073Qa;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderListIterable;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="org.embeddedt.embeddium.impl.render.chunk.DefaultChunkRenderer",remap=false)
public abstract class TerrainScene073RenderProbe {
    @Inject(method="render",at=@At(value="INVOKE",target="Lorg/embeddedt/embeddium/impl/render/chunk/DefaultChunkRenderer;configureShaderInterface(Lorg/embeddedt/embeddium/impl/render/chunk/shader/ChunkShaderInterface;)V",shift=At.Shift.AFTER),remap=false)
    private void terrain073$configured(ChunkRenderMatrices matrices,CommandList device,ChunkRenderListIterable lists,TerrainRenderPass pass,CameraTransform face,CameraTransform render,CallbackInfo callback){TerrainScene073Qa.configured(this,pass.name(),matrices,face,render);}
    @Inject(method="render",at=@At("TAIL"),remap=false)
    private void terrain073$returned(ChunkRenderMatrices matrices,CommandList device,ChunkRenderListIterable lists,TerrainRenderPass pass,CameraTransform face,CameraTransform render,CallbackInfo callback){TerrainScene073Qa.terrainReturned(pass.name());}
}
