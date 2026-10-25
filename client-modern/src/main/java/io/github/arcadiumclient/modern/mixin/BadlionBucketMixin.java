// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import io.github.arcadiumclient.modern.client.features.AnimationsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class BadlionBucketMixin {
    @Shadow private ItemStack mainHandItem;
    @Shadow private ItemStack offHandItem;
    @Shadow private float mainHandHeight;
    @Shadow private float oMainHandHeight;
    @Shadow private float offHandHeight;
    @Shadow private float oOffHandHeight;
    @Inject(method = "itemUsed", at = @At("HEAD"), cancellable = true)
    private void arcadium$bucket(InteractionHand hand, CallbackInfo ci) {
        var settings = AnimationsConfig.badlion();
        var player = Minecraft.getInstance().player;
        if (settings == null || !settings.fixLiquidPlace() || player == null) return;
        var current = player.getItemInHand(hand);
        if (!(current.getItem() instanceof BucketItem)) return;
        if (hand == InteractionHand.MAIN_HAND) { mainHandItem = current; mainHandHeight = oMainHandHeight = 1; }
        else { offHandItem = current; offHandHeight = oOffHandHeight = 1; }
        ci.cancel();
    }
}
