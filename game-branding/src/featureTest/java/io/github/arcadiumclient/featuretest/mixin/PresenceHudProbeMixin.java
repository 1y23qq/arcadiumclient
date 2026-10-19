package io.github.arcadiumclient.featuretest.mixin;

import io.github.arcadiumclient.featuretest.PresenceQa;
import net.minecraft.client.gui.GameGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameGui.class)
public class PresenceHudProbeMixin {
    @Inject(method = "render(F)V", at = @At("TAIL")) private void arcadium$presenceTab(float tickDelta, CallbackInfo ci) { PresenceQa.drawTab(); }
}
