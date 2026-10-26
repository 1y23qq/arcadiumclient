package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.ReleaseQa;
import io.github.arcadiumclient.runtime.qa.SessionUiQa;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class ReleaseQaMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void release$testTick(CallbackInfo ci) { if (SessionUiQa.enabled()) SessionUiQa.tick(); else ReleaseQa.tick(); }
    @Inject(method = "runGame", at = @At("TAIL"))
    private void release$testFrame(CallbackInfo ci) { if (!SessionUiQa.enabled()) ReleaseQa.frame(); }
    @Inject(method = "reloadResources", at = @At("HEAD"))
    private void release$resourceReload(CallbackInfo ci) { if (SessionUiQa.enabled()) SessionUiQa.resourceReloads++; }
}
