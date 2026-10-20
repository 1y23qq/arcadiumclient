// Copyright (c) 2026 0aurorq3.
// SPDX-License-Identifier: MIT
package io.github.arcadiumclient.performance.mixin;

import io.github.arcadiumclient.performance.terrain.TerrainCapture;
import io.github.arcadiumclient.performance.terrain.TerrainRenderer;
import io.github.arcadiumclient.performance.terrain.TerrainVertices;
import java.util.Arrays;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.client.render.world.RenderChunk;
import net.minecraft.entity.living.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderChunk.class)
public abstract class RenderChunkTerrainMixin {
    @Shadow private int glList;
    @Shadow public int originX;
    @Shadow public int originY;
    @Shadow public int originZ;
    @Unique private TerrainCapture arcadium$terrainCapture;

    @Inject(method = "endLayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/vertex/Tesselator;end()I"))
    private void arcadium$captureSortedVertices(int layer, LivingEntity camera, CallbackInfo ci) {
        arcadium$terrainCapture = null;
        if (!TerrainRenderer.enabled()) return;
        TesselatorTerrainAccessor tess = (TesselatorTerrainAccessor) Tesselator.INSTANCE;
        int count = tess.arcadium$terrainVertexCount();
        int format = (tess.arcadium$terrainTextured() ? TerrainCapture.TEXTURE : 0)
                | (tess.arcadium$terrainLight() ? TerrainCapture.LIGHT : 0)
                | (tess.arcadium$terrainColored() ? TerrainCapture.COLOR : 0)
                | (tess.arcadium$terrainNormals() ? TerrainCapture.NORMAL : 0);
        arcadium$terrainCapture = new TerrainCapture(Arrays.copyOf(tess.arcadium$terrainVertices(),
                Math.multiplyExact(count, TerrainVertices.INTS_PER_VERTEX)), count, tess.arcadium$terrainDrawMode(), format);
    }

    @Inject(method = "endLayer", at = @At("RETURN"))
    private void arcadium$uploadAfterListCompilation(int layer, LivingEntity camera, CallbackInfo ci) {
        TerrainCapture capture = arcadium$terrainCapture;
        arcadium$terrainCapture = null;
        TerrainRenderer.upload(glList + layer, layer, originX, originY, originZ, capture);
    }

    @Inject(method = "reset", at = @At("HEAD"))
    private void arcadium$releaseRepositionedMesh(CallbackInfo ci) {
        arcadium$terrainCapture = null;
        TerrainRenderer.remove(glList);
    }
}
