// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.client.optimizations.NativePlayerPosePipeline;
import io.github.arcadiumclient.runtime.client.optimizations.NativePoseReference072Qa;
import net.minecraft.client.render.model.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = NativePlayerPosePipeline.class, remap = false)
public abstract class ForgePose072NativeEntry {
    @Inject(method = "capture", at = @At("HEAD"), remap = false)
    private static void forgePose$reference(NativePlayerPosePipeline.CaptureState state, Object capture, ModelPart part, float scale,
                                          CallbackInfoReturnable<Boolean> callback) { NativePoseReference072Qa.reference(part, scale); }
}
