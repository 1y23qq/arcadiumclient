// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package org.polyfrost.glintcolorizer.mixin.native1710;

//? if =1.7.10 {
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.ItemInHandRenderer;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.Identifier;
import org.polyfrost.glintcolorizer.legacy1710.NativeGlint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(ItemInHandRenderer.class)
public abstract class NativeHeldGlintMixin {
    @WrapMethod(method = "render")
    private void arcadium$held(LivingEntity entity, ItemStack stack, int layer, Operation<Void> original) {
        NativeGlint.enter(stack, stack.isInItemFrame() ? NativeGlint.FRAMED : NativeGlint.HELD);
        try { original.call(entity, stack, layer); } finally { NativeGlint.exit(); }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/TextureManager;bind(Lnet/minecraft/resource/Identifier;)V"))
    private void arcadium$texture(TextureManager manager, Identifier texture, Operation<Void> original) { NativeGlint.texture(texture); original.call(manager, texture); }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glMatrixMode(I)V"))
    private void arcadium$matrix(int mode, Operation<Void> original) { NativeGlint.matrix(mode); original.call(mode); }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glColor4f(FFFF)V"))
    private void arcadium$light(float r, float g, float b, float a, Operation<Void> original) { NativeGlint.color(r, g, b); original.call(r, g, b, a); }

    @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getTime()J"))
    private long arcadium$time(long time) { return NativeGlint.time(time); }

    @ModifyArgs(method = "render", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glScalef(FFF)V"))
    private void arcadium$scale(Args args) {
        args.set(0, NativeGlint.scale(args.get(0))); args.set(1, NativeGlint.scale(args.get(1))); args.set(2, NativeGlint.scale(args.get(2)));
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V"))
    private void arcadium$rotation(float angle, float x, float y, float z, Operation<Void> original) { original.call(NativeGlint.rotation(angle, x, y, z), x, y, z); }
}
//?}
