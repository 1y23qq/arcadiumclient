// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import io.github.arcadiumclient.modern.client.features.AnimationsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class BadlionInventoryMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("RETURN"))
    private void arcadium$inventory(LivingEntity player, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
        var mc = Minecraft.getInstance();
        var settings = AnimationsConfig.badlion();
        if (player == mc.player && settings != null && settings.inventory() && (mc.screen instanceof InventoryScreen || mc.screen instanceof CreativeModeInventoryScreen)) {
            state.walkAnimationSpeed = 0;
            state.walkAnimationPos = 0;
        }
    }
}
