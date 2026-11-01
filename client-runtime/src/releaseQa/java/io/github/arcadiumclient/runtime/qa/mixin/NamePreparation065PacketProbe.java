// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.client.optimizations.GpuNameTags;
import io.github.arcadiumclient.runtime.qa.NamePreparation065Qa;
import net.minecraft.client.render.vertex.BufferBuilder;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GpuNameTags.class, remap = false)
public abstract class NamePreparation065PacketProbe {
    @Inject(method = "queue", at = @At("HEAD"), cancellable = true)
    private void names$packet(BufferBuilder builder, Object pass, int[] source, int length, int stride, int color, boolean text, int alpha,
                              Matrix4fc matrix, float x, float y, float u, float v, CallbackInfoReturnable<Boolean> callback) {
        if (NamePreparation065Qa.packet(pass, source, length, stride, color, text, alpha, matrix, x, y, u, v)) callback.setReturnValue(true);
    }
}
