// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.ItemInHandRenderer;
import io.github.arcadiumclient.runtime.legacy.GlStateManager;
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
    @Shadow private Minecraft minecraft;
    @Shadow private ItemStack itemInHand;
    @Shadow private int selectedSlot;
    @Shadow private float handHeight;
    @Shadow private float lastHandHeight;

    @WrapMethod(method = "renderHand")
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
