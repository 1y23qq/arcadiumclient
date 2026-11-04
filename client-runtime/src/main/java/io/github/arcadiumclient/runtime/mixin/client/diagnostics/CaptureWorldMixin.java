// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client.diagnostics;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.client.diagnostics.PerformanceCapture;
import net.minecraft.client.render.Culler;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(WorldRenderer.class)
public abstract class CaptureWorldMixin {
    @WrapMethod(method = "renderEntities")
    private void arcadium$captureEntities(Entity camera, Culler culler, float delta, Operation<Void> original) {
        PerformanceCapture.phaseBegin(3);
        try { original.call(camera, culler, delta); } finally { PerformanceCapture.phaseEnd(3); }
    }
    @WrapMethod(method = "render(Lnet/minecraft/client/render/block/BlockLayer;DILnet/minecraft/entity/Entity;)I")
    private int arcadium$captureTerrain(BlockLayer layer, double delta, int pass, Entity camera, Operation<Integer> original) {
        PerformanceCapture.phaseBegin(4);
        try { return original.call(layer, delta, pass, camera); } finally { PerformanceCapture.phaseEnd(4); }
    }
    @WrapMethod(method = "compileChunksUntil")
    private void arcadium$captureChunks(long deadline, Operation<Void> original) {
        PerformanceCapture.phaseBegin(9);
        try { original.call(deadline); } finally { PerformanceCapture.phaseEnd(9); }
    }
}
