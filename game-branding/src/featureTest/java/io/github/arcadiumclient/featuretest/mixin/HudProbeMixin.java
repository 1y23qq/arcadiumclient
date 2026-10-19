package io.github.arcadiumclient.featuretest.mixin;
import io.github.arcadiumclient.featuretest.FeatureQa;
import io.github.arcadiumclient.featuretest.FeatureQa028;
import io.github.arcadiumclient.featuretest.ForgeQa;

import net.minecraft.client.gui.GameGui;
import net.minecraft.entity.living.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameGui.class)
public abstract class HudProbeMixin {
    @Inject(method = "render(F)V", at = @At("TAIL"))
    private void arcadium$tabEvidence(float tickDelta, CallbackInfo callback) { if (FeatureQa028.enabled()) FeatureQa028.drawTabOverlay(); else FeatureQa.drawTabOverlay(); }

    @Inject(method = "renderItemSlot", at = @At("HEAD"))
    private void arcadium$hotbarMatrix(int slot, int x, int y, float tickDelta, PlayerEntity player, CallbackInfo callback) {
        if (ForgeQa.enabled()) ForgeQa.recordHotbar(slot, x, y);
        FeatureQa.hotbarMatrix();
    }
}
