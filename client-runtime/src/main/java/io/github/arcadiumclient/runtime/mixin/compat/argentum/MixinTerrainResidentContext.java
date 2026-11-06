// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.TerrainResidentScene;
import io.github.arcadiumclient.runtime.client.optimizations.GpuTerrainCommands;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderer;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
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
@Mixin(targets="org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager",remap=false)
public abstract class MixinTerrainResidentContext {
    @WrapOperation(method="renderLayer",at=@At(value="INVOKE",target="Lorg/embeddedt/embeddium/impl/render/chunk/ChunkRenderer;render(Lorg/embeddedt/embeddium/impl/render/chunk/ChunkRenderMatrices;Lorg/embeddedt/embeddium/impl/gl/device/CommandList;Lorg/embeddedt/embeddium/impl/render/chunk/lists/ChunkRenderListIterable;Lorg/embeddedt/embeddium/impl/render/chunk/terrain/TerrainRenderPass;Lorg/embeddedt/embeddium/impl/render/viewport/CameraTransform;Lorg/embeddedt/embeddium/impl/render/viewport/CameraTransform;)V"),remap=false)
    private void terrain072$scope(ChunkRenderer renderer,ChunkRenderMatrices matrices,CommandList device,ChunkRenderListIterable originalLists,TerrainRenderPass pass,CameraTransform face,CameraTransform render,Operation<Void> original){
        if(!GpuTerrainCommands.residentRequested()){original.call(renderer,matrices,device,originalLists,pass,face,render);return;}
        var token=TerrainResidentScene.enter((RenderSectionManager)(Object)this,renderer,matrices,device,originalLists,pass,face,render);
        try{original.call(renderer,matrices,device,originalLists,pass,face,render);}finally{TerrainResidentScene.exit(token);}
    }
    @Inject(method="destroy",at=@At("HEAD"),remap=false)
    private void terrain072$managerDestroyed(CallbackInfo callback){if(GpuTerrainCommands.residentAttached())TerrainResidentScene.managerDeleted(this);}
}
