// Copyright (c) 2026 0aurorq3.
// SPDX-License-Identifier: MIT
package io.github.arcadiumclient.performance.mixin;

import io.github.arcadiumclient.performance.terrain.TerrainRenderer;
import java.nio.IntBuffer;
import net.minecraft.client.render.world.ChunkRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkRenderer.class)
public abstract class ChunkRendererTerrainMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL11;glCallLists(Ljava/nio/IntBuffer;)V", remap = false))
    private void arcadium$submitSharedTerrainBuffers(IntBuffer lists) {
        TerrainRenderer.draw(lists);
    }
}
