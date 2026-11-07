// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.forge;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.LegacyForgeCameraTransforms;
import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.client.render.model.block.BakedModel;
import net.minecraft.client.resource.model.ModelTransformations;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@IfModLoaded("overflowanimations")
@Mixin(value = ItemRenderer.class, priority = 900)
public abstract class MixinLegacyItemCameraScope {
    @WrapMethod(method = "renderItemInHand(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/block/BakedModel;Lnet/minecraft/client/resource/model/ModelTransformations$Type;)V")
    private void arcadium$legacyCameraScope(ItemStack stack, BakedModel model,
                                            ModelTransformations.Type transform, Operation<Void> original) {
        int token = LegacyForgeCameraTransforms.enter(stack, model, transform);
        try {
            original.call(stack, model, transform);
        } finally {
            LegacyForgeCameraTransforms.exit(token);
        }
    }
}
