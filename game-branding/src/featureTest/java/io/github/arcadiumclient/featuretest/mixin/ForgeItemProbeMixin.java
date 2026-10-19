package io.github.arcadiumclient.featuretest.mixin;

import io.github.arcadiumclient.featuretest.ForgeQa;
import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.client.render.model.block.ModelTransformations;
import net.minecraft.client.resource.model.BakedModel;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class ForgeItemProbeMixin {
    @Inject(method = "renderItemInHand(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/resource/model/BakedModel;Lnet/minecraft/client/render/model/block/ModelTransformations$Type;)V", at = @At("HEAD"))
    private void arcadium$begin(ItemStack stack, BakedModel model, ModelTransformations.Type transform, CallbackInfo callback) {
        if (ForgeQa.enabled()) ForgeQa.beginItem(transform);
    }

    @Inject(method = "renderItemInHand(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/resource/model/BakedModel;Lnet/minecraft/client/render/model/block/ModelTransformations$Type;)V", at = @At("TAIL"))
    private void arcadium$end(ItemStack stack, BakedModel model, ModelTransformations.Type transform, CallbackInfo callback) {
        if (ForgeQa.enabled()) ForgeQa.endItem();
    }

    @Inject(method = "renderItemInHand(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/resource/model/BakedModel;Lnet/minecraft/client/render/model/block/ModelTransformations$Type;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/ItemRenderer;renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/resource/model/BakedModel;)V"))
    private void arcadium$beforeCapturedDraw(ItemStack stack, BakedModel model, ModelTransformations.Type transform, CallbackInfo callback) {
        if (ForgeQa.enabled()) ForgeQa.recordHeldBlock(stack, model, transform);
    }

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/resource/model/BakedModel;)V", at = @At("HEAD"))
    private void arcadium$draw(ItemStack stack, BakedModel model, CallbackInfo callback) {
        if (ForgeQa.enabled()) ForgeQa.recordItem(stack, model);
    }
}
