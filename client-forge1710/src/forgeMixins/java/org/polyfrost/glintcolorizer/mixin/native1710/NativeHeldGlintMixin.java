// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package org.polyfrost.glintcolorizer.mixin.native1710;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.polyfrost.glintcolorizer.legacy1710.NativeGlint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ItemRenderer.class, remap = false)
public abstract class NativeHeldGlintMixin {
    @WrapMethod(method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V")
    private void arcadium$held(EntityLivingBase entity, ItemStack stack, int layer, net.minecraftforge.client.IItemRenderer.ItemRenderType type, Operation<Void> original) {
        NativeGlint.enter(stack, stack.func_82839_y() ? NativeGlint.FRAMED : NativeGlint.HELD);
        try { original.call(entity, stack, layer, type); } finally { NativeGlint.exit(); }
    }

    @WrapOperation(method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureManager;func_110577_a(Lnet/minecraft/util/ResourceLocation;)V"))
    private void arcadium$texture(TextureManager manager, ResourceLocation texture, Operation<Void> original) { NativeGlint.texture(texture); original.call(manager, texture); }

    @WrapOperation(method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glMatrixMode(I)V"))
    private void arcadium$matrix(int mode, Operation<Void> original) { NativeGlint.matrix(mode); original.call(mode); }

    @WrapOperation(method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glColor4f(FFFF)V"))
    private void arcadium$light(float r, float g, float b, float a, Operation<Void> original) { NativeGlint.color(r, g, b); original.call(r, g, b, a); }

    @ModifyExpressionValue(method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;func_71386_F()J"))
    private long arcadium$time(long time) { return NativeGlint.time(time); }

    @WrapOperation(method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glScalef(FFF)V"))
    private void arcadium$scale(float x, float y, float z, Operation<Void> original) {
        original.call(NativeGlint.scale(x), NativeGlint.scale(y), NativeGlint.scale(z));
    }

    @WrapOperation(method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V"))
    private void arcadium$rotation(float angle, float x, float y, float z, Operation<Void> original) { original.call(NativeGlint.rotation(angle, x, y, z), x, y, z); }
}
