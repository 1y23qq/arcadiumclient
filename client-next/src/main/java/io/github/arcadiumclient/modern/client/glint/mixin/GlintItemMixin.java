// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.glint.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.arcadiumclient.modern.client.glint.ModernGlint;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemFeatureRenderer.class)
public class GlintItemMixin {
    @WrapMethod(method = "prepareFoilSubmit")
    private void arcadium$sequentialStrokes(ItemFeatureRenderer.Submit submit, Operation<Void> original) {
        if (!io.github.arcadiumclient.modern.client.glint.GlintConfig.INSTANCE.getEnabled() || submit.foilType() == net.minecraft.client.renderer.item.ItemStackRenderState.FoilType.NONE) { original.call(submit); return; }
        int previous = ModernGlint.scopeStroke(0);
        try {
            original.call(submit);
            ModernGlint.scopeStroke(1);
            original.call(submit);
        } finally { ModernGlint.scopeStroke(previous); }
    }
    @WrapOperation(method = "getFoilBuffer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/ItemFeatureRenderer;getVertexBuilder(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private VertexConsumer arcadium$glint(ItemFeatureRenderer renderer, RenderType type, Operation<VertexConsumer> original) {
        return ModernGlint.vertices(selected -> original.call(renderer, selected), type);
    }
}
