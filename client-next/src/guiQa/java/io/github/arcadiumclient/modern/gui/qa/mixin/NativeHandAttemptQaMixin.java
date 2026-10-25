// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.gui.qa.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.arcadiumclient.modern.gui.qa.NativeRuntimeProbe;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public abstract class NativeHandAttemptQaMixin {
    @WrapOperation(method = "renderItemInHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;submitHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V"))
    private void arcadium$attempt(ItemInHandRenderer renderer, float delta, PoseStack pose, SubmitNodeCollector collector,
            LocalPlayer player, int light, Operation<Void> original) {
        NativeRuntimeProbe.handAttempt();
        original.call(renderer, delta, pose, collector, player, light);
    }
}
