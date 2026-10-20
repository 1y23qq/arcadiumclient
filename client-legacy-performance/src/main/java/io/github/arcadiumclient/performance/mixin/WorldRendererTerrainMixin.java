// Copyright (c) 2026 0aurorq3.
// SPDX-License-Identifier: MIT
package io.github.arcadiumclient.performance.mixin;

import io.github.arcadiumclient.performance.terrain.TerrainRenderer;
import net.minecraft.client.render.world.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererTerrainMixin {
    @Shadow private int chunksGlList;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void arcadium$registerVanillaListRange(CallbackInfo ci) {
        TerrainRenderer.configureLists(chunksGlList, 34 * 34 * 16 * 3);
    }

    @Inject(method = {"reload", "setWorld", "releaseGlLists"}, at = @At("HEAD"))
    private void arcadium$releaseTerrainArenas(CallbackInfo ci) {
        TerrainRenderer.reset();
    }
}
