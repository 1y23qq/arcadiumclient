// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client.diagnostics;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.client.diagnostics.PerformanceCapture;
import net.minecraft.client.gui.GameGui;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GameGui.class)
public abstract class CaptureHudMixin {
    @WrapMethod(method = "render(F)V")
    private void arcadium$captureHud(float delta, Operation<Void> original) {
        PerformanceCapture.phaseBegin(6);
        try { original.call(delta); } finally { PerformanceCapture.phaseEnd(6); }
    }
}
