//? if =1.7.10 {
package dev.microcontrollers.droppeditemtweaks.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.microcontrollers.droppeditemtweaks.config.DroppedItemTweaksConfig;
import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.client.render.texture.Sprite;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class DroppedItems1710Mixin {
    @ModifyExpressionValue(method = "render(Lnet/minecraft/entity/ItemEntity;DDDFF)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/math/MathHelper;sin(F)F"))
    private float droppeditems$staticBobbing(float value) { return DroppedItemTweaksConfig.staticItems ? -1 : value; }

    @ModifyVariable(method = "render(Lnet/minecraft/entity/ItemEntity;DDDFF)V", index = 13,
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTranslatef(FFF)V", ordinal = 0, remap = false))
    private int droppeditems$blockCount(int original, ItemEntity entity, double x, double y, double z, float yaw, float tickDelta) {
        return DroppedItemTweaksConfig.dropStackCount == 0 ? original : Math.min(DroppedItemTweaksConfig.dropStackCount, entity.getItem().size);
    }

    @ModifyVariable(method = "render(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/texture/Sprite;IFFFF)V",
            index = 3, at = @At("STORE"))
    private int droppeditems$fancyCount(int original, ItemEntity entity, Sprite sprite, int count,
                                        float tickDelta, float red, float green, float blue) {
        return DroppedItemTweaksConfig.dropStackCount == 0 ? original : Math.min(DroppedItemTweaksConfig.dropStackCount, entity.getItem().size);
    }

    @Inject(method = "render(Lnet/minecraft/entity/ItemEntity;DDDFF)V", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL11;glTranslatef(FFF)V", ordinal = 0, shift = At.Shift.AFTER, remap = false))
    private void droppeditems$scale(ItemEntity entity, double x, double y, double z, float yaw, float tickDelta, CallbackInfo callback) {
        float scale = DroppedItemTweaksConfig.itemScale;
        if (DroppedItemTweaksConfig.uhcOverlay != 0) {
            Item item = entity.getItem().getItem();
            if (DroppedItemTweaksConfig.uhcItemListLegacy.contains(Item.REGISTRY.getKey(item).toString())) scale = DroppedItemTweaksConfig.uhcOverlay;
        }
        if (scale != 1) GL11.glScalef(scale, scale, scale);
    }
}
//?}
