// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client.diagnostics;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.client.diagnostics.PerformanceCapture;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GameRenderer.class)
public abstract class CaptureRendererMixin {
    @WrapMethod(method = "render(FJ)V")
    private void arcadium$captureRender(float delta, long deadline, Operation<Void> original) {
        PerformanceCapture.phaseBegin(1);
        try { original.call(delta, deadline); } finally { PerformanceCapture.phaseEnd(1); }
    }
    @WrapMethod(method = "renderWorld(FJ)V")
    private void arcadium$captureWorld(float delta, long deadline, Operation<Void> original) {
        PerformanceCapture.phaseBegin(2);
        try { original.call(delta, deadline); } finally { PerformanceCapture.phaseEnd(2); }
    }
}
