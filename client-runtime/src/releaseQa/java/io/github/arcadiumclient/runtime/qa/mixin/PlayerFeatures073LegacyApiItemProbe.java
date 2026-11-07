// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.qa.PlayerFeatures073LegacyApiOracle;
import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.client.render.model.block.BakedModel;
import net.minecraft.client.resource.model.ModelTransformations;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(ItemRenderer.class)
public abstract class PlayerFeatures073LegacyApiItemProbe {
    @WrapMethod(method="renderItemInHand(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/block/BakedModel;Lnet/minecraft/client/resource/model/ModelTransformations$Type;)V")
    private void features073$scope(ItemStack stack,BakedModel model,ModelTransformations.Type type,Operation<Void> original){int token=PlayerFeatures073LegacyApiOracle.enter(stack,model,type);try{original.call(stack,model,type);}finally{PlayerFeatures073LegacyApiOracle.leave(token);}}
}
