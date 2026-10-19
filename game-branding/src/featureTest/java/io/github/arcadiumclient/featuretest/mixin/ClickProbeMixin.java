package io.github.arcadiumclient.featuretest.mixin;
import io.github.arcadiumclient.featuretest.FeatureQa;

import net.minecraft.client.ClientPlayerInteractionManager;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClickProbeMixin {
    @Inject(method = "clickSlot", at = @At("HEAD"), cancellable = true)
    private void arcadium$offlineClick(int menu, int slot, int button, int action, PlayerEntity player, CallbackInfoReturnable<ItemStack> callback) {
        if (FeatureQa.clickScope()) callback.setReturnValue(FeatureQa.click(slot, button, action, player));
    }
}
