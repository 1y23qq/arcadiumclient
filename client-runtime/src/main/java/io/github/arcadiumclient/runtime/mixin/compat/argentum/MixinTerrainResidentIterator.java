// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.TerrainResidentScene;
import io.github.arcadiumclient.runtime.client.optimizations.GpuTerrainCommands;
import java.util.Iterator;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.DefaultChunkRenderer;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderListIterable;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@IfModLoaded("argentum")
@Mixin(targets="org.embeddedt.embeddium.impl.render.chunk.DefaultChunkRenderer",remap=false)
public abstract class MixinTerrainResidentIterator {
    @Inject(method="render",at=@At(value="INVOKE",target="Lorg/embeddedt/embeddium/impl/render/chunk/DefaultChunkRenderer;configureShaderInterface(Lorg/embeddedt/embeddium/impl/render/chunk/shader/ChunkShaderInterface;)V",shift=At.Shift.AFTER),remap=false)
    private void terrain072$preparedIterator(ChunkRenderMatrices matrices,CommandList device,ChunkRenderListIterable lists,TerrainRenderPass pass,CameraTransform face,CameraTransform render,CallbackInfo callback,@Local LocalRef<Iterator<ChunkRenderList>> iterator,@Local(ordinal=0) boolean cull){
        if(GpuTerrainCommands.residentRequested())iterator.set(TerrainResidentScene.prepare((DefaultChunkRenderer)(Object)this,iterator.get(),cull));
    }
}
