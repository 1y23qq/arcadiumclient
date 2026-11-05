// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.TerrainCommandBackend;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.BatchAssembler;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.CachedBatch;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@IfModLoaded("argentum")
@Mixin(targets = "org.embeddedt.embeddium.impl.render.chunk.DefaultChunkRenderer", remap = false)
public abstract class MixinGpuTerrainCommands {
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lorg/embeddedt/embeddium/impl/render/chunk/data/SectionRenderDataStorage;getCachedMultiDrawBatch(Lorg/embeddedt/embeddium/impl/render/chunk/data/SectionRenderDataStorage$BatchCacheParams;)Lorg/embeddedt/embeddium/impl/render/chunk/multidraw/CachedBatch;"), remap = false)
    private CachedBatch arcadium$selectBatch(SectionRenderDataStorage storage, SectionRenderDataStorage.BatchCacheParams params,
                                            Operation<CachedBatch> original, @Local(argsOnly = true) TerrainRenderPass pass) {
        CachedBatch cached = original.call(storage, params);
        return TerrainCommandBackend.accept(cached, pass) ? cached : null;
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lorg/embeddedt/embeddium/impl/render/chunk/multidraw/BatchAssembler;createCachedBatch(Lorg/embeddedt/embeddium/impl/render/chunk/region/RenderRegion;Lorg/embeddedt/embeddium/impl/render/chunk/data/SectionRenderDataStorage;Lorg/embeddedt/embeddium/impl/render/chunk/lists/ChunkRenderList;Lorg/embeddedt/embeddium/impl/render/viewport/CameraTransform;Lorg/embeddedt/embeddium/impl/render/chunk/terrain/TerrainRenderPass;ZLorg/embeddedt/embeddium/impl/gl/device/CommandList;Lorg/embeddedt/embeddium/impl/render/chunk/multidraw/BatchAssembler$TessellationProvider;)Lorg/embeddedt/embeddium/impl/render/chunk/multidraw/CachedBatch;"), remap = false)
    private CachedBatch arcadium$residentBatch(RenderRegion region, SectionRenderDataStorage storage, ChunkRenderList list,
                                              CameraTransform camera, TerrainRenderPass pass, boolean faceCulling,
                                              CommandList device, BatchAssembler.TessellationProvider tessellations,
                                              Operation<CachedBatch> original) {
        CachedBatch cached = TerrainCommandBackend.create(region, storage, list, camera, pass, faceCulling, device, tessellations);
        return cached != null ? cached : original.call(region, storage, list, camera, pass, faceCulling, device, tessellations);
    }
}
