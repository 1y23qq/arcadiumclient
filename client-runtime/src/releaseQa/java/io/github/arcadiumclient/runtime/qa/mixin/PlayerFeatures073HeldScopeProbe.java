// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.qa.PlayerFeatures073Qa;
import io.github.arcadiumclient.runtime.qa.VoxelSkin073PerformanceQa;
import net.minecraft.client.render.ItemInHandRenderer;
import net.minecraft.client.resource.model.ModelTransformations;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(ItemInHandRenderer.class)
public abstract class PlayerFeatures073HeldScopeProbe {
    @WrapMethod(method="render(Lnet/minecraft/entity/living/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/resource/model/ModelTransformations$Type;)V")
    private void features073$held(LivingEntity entity,ItemStack stack,ModelTransformations.Type transform,Operation<Void> original){int costToken=VoxelSkin073PerformanceQa.heldEnter(entity,stack,transform);int token=PlayerFeatures073Qa.heldEnter(entity,transform);try{original.call(entity,stack,transform);VoxelSkin073PerformanceQa.heldReturned(costToken);}finally{PlayerFeatures073Qa.heldExit(token);}}
}
