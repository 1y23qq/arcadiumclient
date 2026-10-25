// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.glint.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.arcadiumclient.modern.client.glint.GlintItemData;
import io.github.arcadiumclient.modern.client.glint.ModernGlint;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ModelFeatureRenderer.class)
public class GlintModelMixin {
    @WrapMethod(method = "prepareModel")
    private void arcadium$restore(ModelFeatureRenderer.Submit<?> submit, Operation<Void> original) {
        var previous = ModernGlint.scope(((GlintItemData) (Object) submit).arcadium$glintData());
        var previousContext = ModernGlint.scopeContext(((io.github.arcadiumclient.modern.client.glint.GlintSubmitContext) (Object) submit).arcadium$glintContext());
        int previousStroke = ModernGlint.scopeStroke(0);
        try {
            original.call(submit);
            if (io.github.arcadiumclient.modern.client.glint.GlintConfig.INSTANCE.getEnabled() && ModernGlint.foilType(submit.renderType())) {
                ModernGlint.scopeStroke(1);
                original.call(submit);
            }
        } finally { ModernGlint.scope(previous); ModernGlint.scopeContext(previousContext); ModernGlint.scopeStroke(previousStroke); }
    }
    @WrapOperation(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer;getVertexBuilder(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private VertexConsumer arcadium$glint(ModelFeatureRenderer renderer, RenderType type, Operation<VertexConsumer> original) {
        return ModernGlint.vertices(selected -> original.call(renderer, selected), type);
    }
}
