package io.github.arcadiumclient.featuretest.mixin;

import io.github.arcadiumclient.featuretest.PresenceQa;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class PresenceGameProbeMixin {
    @Inject(method = "tick", at = @At("TAIL")) private void arcadium$presenceTick(CallbackInfo ci) { PresenceQa.tick(); }
    @Inject(method = "runGame", at = @At("HEAD")) private void arcadium$presenceBegin(CallbackInfo ci) { PresenceQa.beginFrame(); }
    @Inject(method = "runGame", at = @At("TAIL")) private void arcadium$presenceFrame(CallbackInfo ci) { PresenceQa.afterFrame(); }
}
