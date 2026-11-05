// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.ModernGlint072Qa;
import io.github.arcadiumclient.runtime.qa.ModernGlintPixelTrace072Qa;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.rdh.argentum.api.render.instancing.InstancedGeometryBuffer", remap = false)
public abstract class ModernGlint072DrawProbe {
    @Inject(method = "draw(Lorg/embeddedt/embeddium/impl/gl/device/CommandList;II)V", at = @At("HEAD"), remap = false)
    private void modern$draw(org.embeddedt.embeddium.impl.gl.device.CommandList commands, int vertices, int instances, CallbackInfo callback) { ModernGlint072Qa.draw(); }

    @WrapOperation(method = "draw(Lorg/embeddedt/embeddium/impl/gl/device/CommandList;II)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/ARBDrawInstanced;glDrawArraysInstancedARB(IIII)V", remap = false), remap = false)
    private void modern$pixels(int mode, int first, int vertices, int instances, Operation<Void> original) {
        int token = ModernGlintPixelTrace072Qa.beforeGpu(this, mode, first, vertices, instances);
        try {
            original.call(mode, first, vertices, instances);
            io.github.arcadiumclient.runtime.client.optimizations.ModernArmorAuxiliary072Qa.drawCompleted();
        } finally { ModernGlintPixelTrace072Qa.after(token); }
    }
}
