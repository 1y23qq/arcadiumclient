// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client.diagnostics;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.client.diagnostics.PerformanceCapture;
import net.minecraft.client.ParticleManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ParticleManager.class)
public abstract class CaptureParticlesMixin {
    @WrapMethod(method = {"render", "renderLit"})
    private void arcadium$captureParticles(Entity camera, float delta, Operation<Void> original) {
        PerformanceCapture.phaseBegin(5);
        try { original.call(camera, delta); } finally { PerformanceCapture.phaseEnd(5); }
    }
}
