// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.gui.qa.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.arcadiumclient.modern.client.glint.GlintVertices;
import io.github.arcadiumclient.modern.gui.qa.NativeRuntimeProbe;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GlintVertices.class)
public abstract class NativeGlintTintQaMixin {
    @Shadow @Final private int color;
    @Inject(method = {"setColor(I)Lcom/mojang/blaze3d/vertex/VertexConsumer;", "setColor(IIII)Lcom/mojang/blaze3d/vertex/VertexConsumer;"}, at = @At("RETURN"))
    private void arcadium$writtenTint(CallbackInfoReturnable<VertexConsumer> cir) { NativeRuntimeProbe.vertexTint(color); }
}
