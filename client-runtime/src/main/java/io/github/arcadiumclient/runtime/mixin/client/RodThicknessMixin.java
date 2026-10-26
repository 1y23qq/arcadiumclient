// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.
package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.arcadiumclient.runtime.client.features.RodThickness;
import net.minecraft.client.render.entity.FishingBobberRenderer;
import net.minecraft.client.render.vertex.Tesselator;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FishingBobberRenderer.class)
public abstract class RodThicknessMixin {
    @WrapOperation(method = "render(Lnet/minecraft/entity/FishingBobberEntity;DDDFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;end()V", ordinal = 1))
    private void arcadium$lineWidth(Tesselator buffer, Operation<Void> draw) {
        if (!RodThickness.getEnabled()) { draw.call(buffer); return; }
        float previous = GL11.glGetFloat(GL11.GL_LINE_WIDTH);
        try {
            GL11.glLineWidth(RodThickness.lineWidth());
            draw.call(buffer);
        } finally {
            GL11.glLineWidth(previous);
        }
    }
}
