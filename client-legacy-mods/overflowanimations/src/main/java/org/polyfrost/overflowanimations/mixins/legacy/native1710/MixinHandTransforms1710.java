/**
 * OverflowAnimations
 * The all-you-could-want legacy animations mod for modern minecraft versions.
 * Brings back animations from the 1.7/1.8 era and more.
 * <p>
 * Copyright (C) 2024-2027 lowercasebtw
 * Copyright (C) 2024-2027 mixces
 * Copyright (C) 2024-2027 Contributors to the project retain their copyright
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * "MINECRAFT" LINKING EXCEPTION TO THE GPL
 */

package org.polyfrost.overflowanimations.mixins.legacy.native1710;

//? if =1.7.10 {
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.block.Block;
import net.minecraft.client.render.ItemInHandRenderer;
import net.minecraft.client.render.block.BlockRenderer;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.UseAction;
import org.lwjgl.opengl.GL11;
import org.polyfrost.overflowanimations.config.OverflowAnimationsConfig;
import org.polyfrost.overflowanimations.config.category.ItemsConfigCategory;
import org.polyfrost.overflowanimations.legacy.entity.LegacyItemRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class MixinHandTransforms1710 {
    @Shadow private Minecraft minecraft;
    @Shadow private ItemStack itemInHand;

    @Unique private boolean overflowanimations$customize(ItemsConfigCategory settings) {
        return settings.applyCustomizationToBlockItems || itemInHand == null || !(itemInHand.getItem() instanceof BlockItem block)
            || !BlockRenderer.isItem3d(Block.byItem(block).getRenderType());
    }

    @WrapOperation(method = "renderHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/ItemInHandRenderer;render(Lnet/minecraft/entity/living/LivingEntity;Lnet/minecraft/item/ItemStack;I)V"))
    private void overflowanimations$customizeItem(ItemInHandRenderer renderer, LivingEntity entity, ItemStack stack, int pass, Operation<Void> original) {
        ItemsConfigCategory settings = OverflowAnimationsConfig.instance().items;
        if (!overflowanimations$customize(settings) || settings.itemScaleX == 1 && settings.itemScaleY == 1 && settings.itemScaleZ == 1
                && settings.itemOffsetX == 0 && settings.itemOffsetY == 0 && settings.itemOffsetZ == 0
                && settings.itemRotationX == 0 && settings.itemRotationY == 0 && settings.itemRotationZ == 0) {
            original.call(renderer, entity, stack, pass);
            return;
        }
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(settings.itemOffsetX * 0.125F, settings.itemOffsetY * 0.125F, settings.itemOffsetZ * 0.125F);
            GL11.glScalef(settings.itemScaleX, settings.itemScaleY, settings.itemScaleZ);
            GL11.glRotatef(settings.itemRotationX, 1, 0, 0);
            GL11.glRotatef(settings.itemRotationY, 0, 1, 0);
            GL11.glRotatef(settings.itemRotationZ, 0, 0, 1);
            original.call(renderer, entity, stack, pass);
        } finally {
            GL11.glPopMatrix();
        }
    }

    @WrapWithCondition(method = "renderHand", at = {
        @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V", ordinal = 2),
        @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V", ordinal = 3)
    })
    private boolean overflowanimations$handSway(float angle, float x, float y, float z) {
        return !OverflowAnimationsConfig.instance().items.disableHandSway;
    }

    @ModifyExpressionValue(method = "renderHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;getItemUseTimer()I"))
    private int overflowanimations$guiUseVisual(int original) {
        return OverflowAnimationsConfig.instance().fixes.fixItemUsageVisualInGUI && minecraft.screen != null ? 0 : original;
    }

    @ModifyExpressionValue(method = "renderHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;getAttackAnimationProgress(F)F", ordinal = 3))
    private float overflowanimations$usageSwing(float original) {
        return minecraft.player.hasItemInUse() && !OverflowAnimationsConfig.instance().items.itemUsageSwinging ? 0 : original;
    }

    @WrapOperation(method = "renderHand", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTranslatef(FFF)V", ordinal = 2),
        slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/item/UseAction;DRINK:Lnet/minecraft/item/UseAction;"),
                       to = @At(value = "FIELD", target = "Lnet/minecraft/item/UseAction;BLOCK:Lnet/minecraft/item/UseAction;")))
    private void overflowanimations$swingTranslation(float x, float y, float z, Operation<Void> original) {
        ItemsConfigCategory settings = OverflowAnimationsConfig.instance().items;
        if (settings.disableSwingTranslate) return;
        float sx = 1 + settings.swingPositionX, sy = 1 - settings.swingPositionY, sz = 1 + settings.swingPositionZ;
        if (settings.smartSwingScaling) { sx *= settings.itemScaleX; sy *= settings.itemScaleY; sz *= settings.itemScaleZ; }
        original.call(x * sx, y * sy, z * sz);
    }

    @WrapOperation(method = "renderHand", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTranslatef(FFF)V", ordinal = 0),
        slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/item/UseAction;DRINK:Lnet/minecraft/item/UseAction;"),
                       to = @At(value = "FIELD", target = "Lnet/minecraft/item/UseAction;BLOCK:Lnet/minecraft/item/UseAction;")))
    private void overflowanimations$consumeIntensity(float x, float y, float z, Operation<Void> original) {
        ItemsConfigCategory settings = OverflowAnimationsConfig.instance().items;
        GL11.glTranslatef(settings.consumePositionX, settings.consumePositionY, settings.consumePositionZ);
        original.call(x, y * (1 + settings.consumeIntensity) * (settings.scaleConsumeWithItem ? settings.itemScaleY : 1), z);
    }

    @WrapOperation(method = "renderHand", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTranslatef(FFF)V", ordinal = 1),
        slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/item/UseAction;DRINK:Lnet/minecraft/item/UseAction;"),
                       to = @At(value = "FIELD", target = "Lnet/minecraft/item/UseAction;BLOCK:Lnet/minecraft/item/UseAction;")))
    private void overflowanimations$consumeRotation(float x, float y, float z, Operation<Void> original) {
        ItemsConfigCategory settings = OverflowAnimationsConfig.instance().items;
        if (settings.scaleConsumeWithItem) original.call(x * settings.itemScaleX, y * settings.itemScaleY, z * settings.itemScaleZ);
        else original.call(x, y, z);
        GL11.glRotatef(settings.consumeRotationX, 1, 0, 0);
        GL11.glRotatef(settings.consumeRotationY, 0, 1, 0);
        GL11.glRotatef(settings.consumeRotationZ, 0, 0, 1);
    }

    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "renderHand", at = @At("STORE"), index = 17,
        slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/item/UseAction;DRINK:Lnet/minecraft/item/UseAction;"),
                       to = @At(value = "FIELD", target = "Lnet/minecraft/item/UseAction;BLOCK:Lnet/minecraft/item/UseAction;")))
    private float overflowanimations$consumeSpeed(float original, @com.llamalad7.mixinextras.sugar.Local(index = 15) float progress) {
        float speed = OverflowAnimationsConfig.instance().items.consumeSpeed;
        return speed == 0 ? original : 1 - (float) Math.pow(progress, 27 * (1 + speed));
    }

    @WrapOperation(method = "renderHand", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glScalef(FFF)V", ordinal = 0),
        slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/item/UseAction;DRINK:Lnet/minecraft/item/UseAction;"),
                       to = @At(value = "FIELD", target = "Lnet/minecraft/item/UseAction;BLOCK:Lnet/minecraft/item/UseAction;")))
    private void overflowanimations$consumeScale(float x, float y, float z, Operation<Void> original) {
        float scale = 1;
        if (itemInHand != null && minecraft.player.hasItemInUse()) {
            UseAction action = itemInHand.getUseAction();
            if (action == UseAction.EAT || action == UseAction.DRINK) scale += OverflowAnimationsConfig.instance().items.consumeScale;
        }
        original.call(x * scale, y * scale, z * scale);
    }

    @WrapOperation(method = "renderHand", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTranslatef(FFF)V"),
        slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/item/UseAction;BLOCK:Lnet/minecraft/item/UseAction;"),
                       to = @At(value = "FIELD", target = "Lnet/minecraft/item/UseAction;BOW:Lnet/minecraft/item/UseAction;")))
    private void overflowanimations$blockingPosition(float x, float y, float z, Operation<Void> original) {
        ItemsConfigCategory settings = OverflowAnimationsConfig.instance().items;
        original.call(x + settings.blockingPositionX, y + settings.blockingPositionY, z + settings.blockingPositionZ);
        GL11.glRotatef(settings.blockingRotationX, 1, 0, 0);
        GL11.glRotatef(settings.blockingRotationY, 0, 1, 0);
        GL11.glRotatef(settings.blockingRotationZ, 0, 0, 1);
    }

    @Inject(method = "renderHand", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V", ordinal = 2, shift = At.Shift.AFTER),
        slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/item/UseAction;BLOCK:Lnet/minecraft/item/UseAction;"),
                       to = @At(value = "FIELD", target = "Lnet/minecraft/item/UseAction;BOW:Lnet/minecraft/item/UseAction;")))
    private void overflowanimations$blockingScale(float delta, CallbackInfo ci) {
        float scale = 1 + OverflowAnimationsConfig.instance().items.blockingScale;
        if (scale != 1) GL11.glScalef(scale, scale, scale);
    }
}

//?}
