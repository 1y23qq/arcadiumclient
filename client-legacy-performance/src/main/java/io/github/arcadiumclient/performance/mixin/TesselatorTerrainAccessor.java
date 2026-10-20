// Copyright (c) 2026 0aurorq3.
// SPDX-License-Identifier: MIT
package io.github.arcadiumclient.performance.mixin;

import net.minecraft.client.render.vertex.Tesselator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Tesselator.class)
public interface TesselatorTerrainAccessor {
    @Accessor("vertexBuffer") int[] arcadium$terrainVertices();
    @Accessor("vertexCount") int arcadium$terrainVertexCount();
    @Accessor("drawMode") int arcadium$terrainDrawMode();
    @Accessor("textured") boolean arcadium$terrainTextured();
    @Accessor("hasBrightness") boolean arcadium$terrainLight();
    @Accessor("colored") boolean arcadium$terrainColored();
    @Accessor("hasNormals") boolean arcadium$terrainNormals();
}
