// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.qa.PlayerFeatures073Qa;
import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.client.render.model.block.BakedModel;
import net.minecraft.client.resource.model.ModelTransformations;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(ItemRenderer.class)
public abstract class PlayerFeatures073ItemDrawProbe {
    @WrapMethod(method="renderItemInHand(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/block/BakedModel;Lnet/minecraft/client/resource/model/ModelTransformations$Type;)V")
    private void features073$camera(ItemStack stack,BakedModel model,ModelTransformations.Type transform,Operation<Void> original){PlayerFeatures073Qa.cameraInput(model);original.call(stack,model,transform);}
    @WrapMethod(method="renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/block/BakedModel;)V")
    private void features073$draw(ItemStack stack,BakedModel model,Operation<Void> original){PlayerFeatures073Qa.itemDraw(model);original.call(stack,model);}
}
