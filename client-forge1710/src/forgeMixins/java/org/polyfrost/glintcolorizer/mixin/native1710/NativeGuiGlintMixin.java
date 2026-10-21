// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package org.polyfrost.glintcolorizer.mixin.native1710;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.polyfrost.glintcolorizer.config.GlintConfig;
import org.polyfrost.glintcolorizer.legacy1710.NativeGlint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = RenderItem.class, remap = false)
public abstract class NativeGuiGlintMixin {
    @Shadow public float field_77023_b;

    @WrapMethod(method = "func_82406_b")
    private void arcadium$gui(FontRenderer font, TextureManager textures, ItemStack stack, int x, int y, Operation<Void> original) {
        NativeGlint.enter(stack, NativeGlint.GUI);
        try { original.call(font, textures, stack, x, y); } finally { NativeGlint.exit(); }
    }

    @WrapMethod(method = "func_76986_a(Lnet/minecraft/entity/item/EntityItem;DDDFF)V")
    private void arcadium$dropped(EntityItem entity, double x, double y, double z, float yaw, float delta, Operation<Void> original) {
        ItemStack stack = entity.func_92059_d();
        NativeGlint.enter(stack, RenderItem.field_82407_g || stack.func_82839_y() ? NativeGlint.FRAMED : NativeGlint.DROPPED);
        try { original.call(entity, x, y, z, yaw, delta); } finally { NativeGlint.exit(); }
    }

    @WrapMethod(method = "func_77018_a")
    private void arcadium$effect(int seed, int x, int y, int width, int height, Operation<Void> original) {
        if (!GlintConfig.INSTANCE.getEnabled()) original.call(seed, x, y, width, height);
        else NativeGlint.gui(x, y, width, height, field_77023_b);
    }

    @WrapOperation(method = {"func_82406_b", "renderDroppedItem(Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/util/IIcon;IFFFFI)V"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureManager;func_110577_a(Lnet/minecraft/util/ResourceLocation;)V"))
    private void arcadium$texture(TextureManager manager, ResourceLocation texture, Operation<Void> original) {
        NativeGlint.texture(texture); original.call(manager, texture);
    }

    @WrapOperation(method = "renderDroppedItem(Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/util/IIcon;IFFFFI)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glMatrixMode(I)V"))
    private void arcadium$matrix(int mode, Operation<Void> original) { NativeGlint.matrix(mode); original.call(mode); }

    @WrapOperation(method = "renderDroppedItem(Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/util/IIcon;IFFFFI)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glColor4f(FFFF)V"))
    private void arcadium$light(float r, float g, float b, float a, Operation<Void> original) { NativeGlint.color(r, g, b); original.call(r, g, b, a); }

    @ModifyExpressionValue(method = "renderDroppedItem(Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/util/IIcon;IFFFFI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;func_71386_F()J"))
    private long arcadium$time(long time) { return NativeGlint.time(time); }

    @WrapOperation(method = "renderDroppedItem(Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/util/IIcon;IFFFFI)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glScalef(FFF)V"))
    private void arcadium$scale(float x, float y, float z, Operation<Void> original) {
        original.call(NativeGlint.scale(x), NativeGlint.scale(y), NativeGlint.scale(z));
    }

    @WrapOperation(method = "renderDroppedItem(Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/util/IIcon;IFFFFI)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V"))
    private void arcadium$rotation(float angle, float x, float y, float z, Operation<Void> original) { original.call(NativeGlint.rotation(angle, x, y, z), x, y, z); }
}
