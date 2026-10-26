package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.ReleaseQa;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class ReleaseQaMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void release$testTick(CallbackInfo ci) { ReleaseQa.tick(); }
    @Inject(method = "runGame", at = @At("TAIL"))
    private void release$testFrame(CallbackInfo ci) { ReleaseQa.frame(); }
}
