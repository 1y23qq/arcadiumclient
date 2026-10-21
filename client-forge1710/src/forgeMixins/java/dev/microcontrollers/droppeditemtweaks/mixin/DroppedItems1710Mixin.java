// SPDX-License-Identifier: LGPL-3.0-only
package dev.microcontrollers.droppeditemtweaks.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.microcontrollers.droppeditemtweaks.config.DroppedItemTweaksConfig;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.entity.RenderItem")
public abstract class DroppedItems1710Mixin {
    @ModifyExpressionValue(method = "func_76986_a(Lnet/minecraft/entity/item/EntityItem;DDDFF)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/util/MathHelper;func_76126_a(F)F"), remap = false)
    private float arcadium$bobbing(float original) {
        return DroppedItemTweaksConfig.staticItems ? -1 : original;
    }

    @ModifyReturnValue(method = {"getMiniBlockCount(Lnet/minecraft/item/ItemStack;B)B", "getMiniItemCount(Lnet/minecraft/item/ItemStack;B)B"}, at = @At("RETURN"), remap = false)
    private byte arcadium$copies(byte original, ItemStack stack, byte vanilla) {
        return DroppedItemTweaksConfig.dropStackCount == 0 ? original
            : (byte) Math.min(DroppedItemTweaksConfig.dropStackCount, stack.field_77994_a);
    }

    @Inject(method = "func_76986_a(Lnet/minecraft/entity/item/EntityItem;DDDFF)V", at = @At(value = "INVOKE",
        target = "Lorg/lwjgl/opengl/GL11;glTranslatef(FFF)V", ordinal = 0, shift = At.Shift.AFTER), remap = false)
    private void arcadium$scale(EntityItem entity, double x, double y, double z, float yaw, float partialTicks, CallbackInfo callback) {
        float scale = DroppedItemTweaksConfig.itemScale;
        if (DroppedItemTweaksConfig.uhcOverlay != 0) {
            Item item = entity.func_92059_d().func_77973_b();
            String id = Item.field_150901_e.func_148750_c(item);
            if (DroppedItemTweaksConfig.uhcItemListLegacy.contains(id)) scale = DroppedItemTweaksConfig.uhcOverlay;
        }
        if (scale != 1) GL11.glScalef(scale, scale, scale);
    }
}
