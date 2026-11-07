// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.arcadiumclient.runtime.client.optimizations.TerrainResidentScene;
import org.embeddedt.embeddium.impl.gl.device.*;
import org.embeddedt.embeddium.impl.gl.tessellation.*;
import org.embeddedt.embeddium.impl.render.chunk.DefaultChunkRenderer;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@IfModLoaded("argentum")
@Mixin(value=DefaultChunkRenderer.class,remap=false)
public abstract class MixinTerrainResidentSubmission {
 @Inject(method="render",at=@At("HEAD")) private void eventSubmissionEntry(CallbackInfo callback){TerrainResidentScene.submissionEntered((DefaultChunkRenderer)(Object)this);}
 @WrapOperation(method="render",at=@At(value="INVOKE",target="Lorg/embeddedt/embeddium/impl/gl/device/MultiDrawBatch;execute(Lorg/embeddedt/embeddium/impl/gl/device/CommandList;Lorg/embeddedt/embeddium/impl/gl/tessellation/GlTessellation;Lorg/embeddedt/embeddium/impl/gl/tessellation/GlPrimitiveType;)V"))
 private void eventSubmission(MultiDrawBatch batch,CommandList device,GlTessellation tess,GlPrimitiveType primitive,Operation<Void> original,@Local RenderRegion region,@Local SectionRenderDataStorage storage,@Local ChunkRenderList list){TerrainResidentScene.submit((DefaultChunkRenderer)(Object)this,batch,device,tess,primitive,region,storage,list,original);}
}
