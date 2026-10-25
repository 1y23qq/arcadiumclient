// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.glint.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.arcadiumclient.modern.client.glint.ModernGlint;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemRenderer.class)
public class GlintItemMixin {
    @WrapMethod(method = "renderItem")
    private static void arcadium$glint(ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay, int[] tints, List<BakedQuad> quads, RenderType type, ItemStackRenderState.FoilType foil, Operation<Void> original) {
        original.call(context, pose, foil == ItemStackRenderState.FoilType.NONE ? buffers : ModernGlint.buffers(buffers, context), light, overlay, tints, quads, type, foil);
    }
    @Inject(method = "getFoilRenderTypes", at = @At("RETURN"), cancellable = true)
    private static void arcadium$specialGlint(RenderType type, boolean solid, boolean foil, CallbackInfoReturnable<List<RenderType>> callback) {
        if (foil) callback.setReturnValue(ModernGlint.specialTypes(callback.getReturnValue()));
    }
}
