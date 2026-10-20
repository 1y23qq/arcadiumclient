//? if >1.7.10 {
/*package dev.microcontrollers.droppeditemtweaks.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.microcontrollers.droppeditemtweaks.config.DroppedItemTweaksConfig;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.model.block.BakedModel;
import org.polyfrost.oneconfig.internal.legacy.GlStateManager;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemEntityRenderer.class)
public class ItemEntityRendererMixin {
    @ModifyExpressionValue(method = "applyItemBobbing", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;sin(F)F"))
    private float makeItemStatic(float value) {
        return DroppedItemTweaksConfig.staticItems ? -1.0F : value;
    }

    @ModifyReturnValue(method = "scaledStackSize", at = @At("RETURN"))
    private static int forceStackAmount(int original, ItemStack item) {
        return DroppedItemTweaksConfig.dropStackCount != 0 ? Math.min(DroppedItemTweaksConfig.dropStackCount, item.size) : original;
    }

    @Inject(method = "applyItemBobbing", at = @At(value = "TAIL"))
    private void scaleItems(ItemEntity entity, double dx, double dy, double dz, float tickDelta, BakedModel model, CallbackInfoReturnable<Integer> cir) {
        Item item = entity.getItem().getItem();
        float scale = DroppedItemTweaksConfig.itemScale;
        if (DroppedItemTweaksConfig.uhcOverlay != 0F && DroppedItemTweaksConfig.uhcItemListLegacy.contains(Item.REGISTRY.getKey(item).toString())) {
            scale = DroppedItemTweaksConfig.uhcOverlay;
        }
        GlStateManager.scalef(scale, scale, scale);
    }
}
*///?}
