// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.PlayerPipeline072Qa;
import io.github.arcadiumclient.runtime.qa.PlayerPipeline072ReloadProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PlayerPipeline072Qa.class, remap = false)
public abstract class PlayerPipeline072ReloadProbeMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private static void arcadium$observeReloadWarmup(CallbackInfo callback) { PlayerPipeline072ReloadProbe.warmup(); }
    @Inject(method = "armCounters", at = @At("HEAD"))
    private static void arcadium$nativeModelReload(CallbackInfo callback) { PlayerPipeline072ReloadProbe.reload(); }
    @Inject(method = "worldRendered", at = @At("HEAD"))
    private static void arcadium$observeReloadFrame(CallbackInfo callback) { PlayerPipeline072ReloadProbe.frame(); }
    @ModifyVariable(method = "finish", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static Throwable arcadium$requireReloadProof(Throwable failure) { return PlayerPipeline072ReloadProbe.finish(failure); }
}
