// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.ItemInHandRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.item.BucketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import org.lwjgl.opengl.GL11;
import io.github.arcadiumclient.runtime.client.features.AnimationsConfig;
import io.github.arcadiumclient.runtime.client.features.BadlionAnimationState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ItemInHandRenderer.class, priority = 900)
public abstract class MixinBadlionItemAnimations {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private ItemStack itemInHand;
    @Shadow private int selectedSlot;
    @Shadow private float handHeight;
    @Shadow private float lastHandHeight;

    @WrapMethod(method = "renderInFirstPerson")
    private void arcadium$leftHand(float tickDelta, Operation<Void> original) {
        BadlionAnimationState settings = AnimationsConfig.badlion();
        if (settings == null || !settings.leftHanded()) {
            original.call(tickDelta);
            return;
        }
        int winding = GL11.glGetInteger(GL11.GL_FRONT_FACE);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.scalef(-1f, 1f, 1f);
            GL11.glFrontFace(winding == GL11.GL_CW ? GL11.GL_CCW : GL11.GL_CW);
            original.call(tickDelta);
        } finally {
            GL11.glFrontFace(winding);
            GlStateManager.popMatrix();
        }
    }

    @ModifyArg(method = "renderInFirstPerson", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/ItemInHandRenderer;applyFirstPersonTransform(FF)V"), index = 1)
    private float arcadium$usageSwing(float vanilla, @Local(argsOnly = true) float tickDelta) {
        BadlionAnimationState settings = AnimationsConfig.badlion();
        if (settings == null || itemInHand == null || minecraft.player == null || minecraft.player.getItemUseTimer() <= 0) return vanilla;
        return settings.swingWhileUsing(itemInHand.getUseAction().name()) ? minecraft.player.getAttackAnimationProgress(tickDelta) : 0f;
    }

    @Inject(method = "applyFirstPersonTransform", at = @At("HEAD"))
    private void arcadium$swordSwing(float equip, float swing, CallbackInfo ci) {
        BadlionAnimationState settings = AnimationsConfig.badlion();
        if (settings != null && settings.swordSwing() && itemInHand != null && itemInHand.getItem() instanceof SwordItem) {
            GlStateManager.scalef(0.85f, 0.85f, 0.85f);
            GlStateManager.translatef(-0.078f, 0.003f, 0.05f);
        }
    }

    @Inject(method = "applyBowNocking", at = @At("HEAD"))
    private void arcadium$bowPosition(float tickDelta, ClientPlayerEntity player, CallbackInfo ci) {
        BadlionAnimationState settings = AnimationsConfig.badlion();
        if (settings != null && settings.bowAnimation()) GlStateManager.translatef(-0.01f, 0.05f, -0.06f);
    }

    @Inject(method = "onItemUsed", at = @At("TAIL"))
    private void arcadium$refreshBucket(CallbackInfo ci) {
        BadlionAnimationState settings = AnimationsConfig.badlion();
        if (settings == null || !settings.fixLiquidPlace() || minecraft.player == null) return;
        ItemStack selected = minecraft.player.inventory.getSelectedItem();
        if (selected != null && selected.getItem() instanceof BucketItem) {
            itemInHand = selected;
            selectedSlot = minecraft.player.inventory.selectedSlot;
            handHeight = lastHandHeight = 1f;
        }
    }
}
