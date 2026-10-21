/**
 * OverflowAnimations
 * The all-you-could-want legacy animations mod for modern field_78455_a versions.
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

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.EnumAction;
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

@Mixin(value = ItemRenderer.class, remap = false)
public abstract class MixinHandTransforms1710 {
    @Shadow private Minecraft field_78455_a;
    @Shadow private ItemStack field_78453_b;

    @Unique private boolean overflowanimations$customize(ItemsConfigCategory settings) {
        return settings.applyCustomizationToBlockItems || field_78453_b == null || !(field_78453_b.func_77973_b() instanceof ItemBlock block)
            || !RenderBlocks.func_147739_a(Block.func_149634_a(block).func_149645_b());
    }

    @WrapOperation(method = "func_78440_a(F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V"))
    private void overflowanimations$customizeItem(ItemRenderer renderer, EntityLivingBase entity, ItemStack stack, int pass, net.minecraftforge.client.IItemRenderer.ItemRenderType type, Operation<Void> original) {
        ItemsConfigCategory settings = OverflowAnimationsConfig.instance().items;
        if (!overflowanimations$customize(settings) || settings.itemScaleX == 1 && settings.itemScaleY == 1 && settings.itemScaleZ == 1
                && settings.itemOffsetX == 0 && settings.itemOffsetY == 0 && settings.itemOffsetZ == 0
                && settings.itemRotationX == 0 && settings.itemRotationY == 0 && settings.itemRotationZ == 0) {
            original.call(renderer, entity, stack, pass, type);
            return;
        }
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(settings.itemOffsetX * 0.125F, settings.itemOffsetY * 0.125F, settings.itemOffsetZ * 0.125F);
            GL11.glScalef(settings.itemScaleX, settings.itemScaleY, settings.itemScaleZ);
            GL11.glRotatef(settings.itemRotationX, 1, 0, 0);
            GL11.glRotatef(settings.itemRotationY, 0, 1, 0);
            GL11.glRotatef(settings.itemRotationZ, 0, 0, 1);
            original.call(renderer, entity, stack, pass, type);
        } finally {
            GL11.glPopMatrix();
        }
    }

    @WrapWithCondition(method = "func_78440_a(F)V", at = {
        @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V", ordinal = 2),
        @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V", ordinal = 3)
    })
    private boolean overflowanimations$handSway(float angle, float x, float y, float z) {
        return !OverflowAnimationsConfig.instance().items.disableHandSway;
    }

    @ModifyExpressionValue(method = "func_78440_a(F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityClientPlayerMP;func_71052_bv()I"))
    private int overflowanimations$guiUseVisual(int original) {
        return OverflowAnimationsConfig.instance().fixes.fixItemUsageVisualInGUI && field_78455_a.field_71462_r != null ? 0 : original;
    }

    @ModifyExpressionValue(method = "func_78440_a(F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityClientPlayerMP;func_70678_g(F)F", ordinal = 3))
    private float overflowanimations$usageSwing(float original) {
        return field_78455_a.field_71439_g.func_71039_bw() && !OverflowAnimationsConfig.instance().items.itemUsageSwinging ? 0 : original;
    }

    @WrapOperation(method = "func_78440_a(F)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTranslatef(FFF)V", ordinal = 2),
        slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/item/EnumAction;drink:Lnet/minecraft/item/EnumAction;"),
                       to = @At(value = "FIELD", target = "Lnet/minecraft/item/EnumAction;block:Lnet/minecraft/item/EnumAction;")))
    private void overflowanimations$swingTranslation(float x, float y, float z, Operation<Void> original) {
        ItemsConfigCategory settings = OverflowAnimationsConfig.instance().items;
        if (settings.disableSwingTranslate) return;
        float sx = 1 + settings.swingPositionX, sy = 1 - settings.swingPositionY, sz = 1 + settings.swingPositionZ;
        if (settings.smartSwingScaling) { sx *= settings.itemScaleX; sy *= settings.itemScaleY; sz *= settings.itemScaleZ; }
        original.call(x * sx, y * sy, z * sz);
    }

    @WrapOperation(method = "func_78440_a(F)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTranslatef(FFF)V", ordinal = 0),
        slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/item/EnumAction;drink:Lnet/minecraft/item/EnumAction;"),
                       to = @At(value = "FIELD", target = "Lnet/minecraft/item/EnumAction;block:Lnet/minecraft/item/EnumAction;")))
    private void overflowanimations$consumeIntensity(float x, float y, float z, Operation<Void> original) {
        ItemsConfigCategory settings = OverflowAnimationsConfig.instance().items;
        GL11.glTranslatef(settings.consumePositionX, settings.consumePositionY, settings.consumePositionZ);
        original.call(x, y * (1 + settings.consumeIntensity) * (settings.scaleConsumeWithItem ? settings.itemScaleY : 1), z);
    }

    @WrapOperation(method = "func_78440_a(F)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTranslatef(FFF)V", ordinal = 1),
        slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/item/EnumAction;drink:Lnet/minecraft/item/EnumAction;"),
                       to = @At(value = "FIELD", target = "Lnet/minecraft/item/EnumAction;block:Lnet/minecraft/item/EnumAction;")))
    private void overflowanimations$consumeRotation(float x, float y, float z, Operation<Void> original) {
        ItemsConfigCategory settings = OverflowAnimationsConfig.instance().items;
        if (settings.scaleConsumeWithItem) original.call(x * settings.itemScaleX, y * settings.itemScaleY, z * settings.itemScaleZ);
        else original.call(x, y, z);
        GL11.glRotatef(settings.consumeRotationX, 1, 0, 0);
        GL11.glRotatef(settings.consumeRotationY, 0, 1, 0);
        GL11.glRotatef(settings.consumeRotationZ, 0, 0, 1);
    }

    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "func_78440_a(F)V", at = @At("STORE"), index = 22,
        slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/item/EnumAction;drink:Lnet/minecraft/item/EnumAction;"),
                       to = @At(value = "FIELD", target = "Lnet/minecraft/item/EnumAction;block:Lnet/minecraft/item/EnumAction;")))
    private float overflowanimations$consumeSpeed(float original, @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) float partialTicks) {
        float speed = OverflowAnimationsConfig.instance().items.consumeSpeed;
        if (speed == 0) return original;
        float progress = (field_78455_a.field_71439_g.func_71052_bv() - partialTicks + 1) / field_78453_b.func_77988_m();
        return 1 - (float) Math.pow(progress, 27 * (1 + speed));
    }

    @WrapOperation(method = "func_78440_a(F)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glScalef(FFF)V", ordinal = 0),
        slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/item/EnumAction;drink:Lnet/minecraft/item/EnumAction;"),
                       to = @At(value = "FIELD", target = "Lnet/minecraft/item/EnumAction;block:Lnet/minecraft/item/EnumAction;")))
    private void overflowanimations$consumeScale(float x, float y, float z, Operation<Void> original) {
        float scale = 1;
        if (field_78453_b != null && field_78455_a.field_71439_g.func_71039_bw()) {
            EnumAction action = field_78453_b.func_77975_n();
            if (action == EnumAction.eat || action == EnumAction.drink) scale += OverflowAnimationsConfig.instance().items.consumeScale;
        }
        original.call(x * scale, y * scale, z * scale);
    }

    @WrapOperation(method = "func_78440_a(F)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTranslatef(FFF)V"),
        slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/item/EnumAction;block:Lnet/minecraft/item/EnumAction;"),
                       to = @At(value = "FIELD", target = "Lnet/minecraft/item/EnumAction;bow:Lnet/minecraft/item/EnumAction;")))
    private void overflowanimations$blockingPosition(float x, float y, float z, Operation<Void> original) {
        ItemsConfigCategory settings = OverflowAnimationsConfig.instance().items;
        original.call(x + settings.blockingPositionX, y + settings.blockingPositionY, z + settings.blockingPositionZ);
        GL11.glRotatef(settings.blockingRotationX, 1, 0, 0);
        GL11.glRotatef(settings.blockingRotationY, 0, 1, 0);
        GL11.glRotatef(settings.blockingRotationZ, 0, 0, 1);
    }

    @Inject(method = "func_78440_a(F)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V", ordinal = 2, shift = At.Shift.AFTER),
        slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/item/EnumAction;block:Lnet/minecraft/item/EnumAction;"),
                       to = @At(value = "FIELD", target = "Lnet/minecraft/item/EnumAction;bow:Lnet/minecraft/item/EnumAction;")))
    private void overflowanimations$blockingScale(float delta, CallbackInfo ci) {
        float scale = 1 + OverflowAnimationsConfig.instance().items.blockingScale;
        if (scale != 1) GL11.glScalef(scale, scale, scale);
    }
}

