// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin.compat;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.modern.client.ModernIdle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@IfModLoaded("dynamic_fps")
@Mixin(targets = "dynamic_fps.impl.config.Serialization", remap = false)
public abstract class DynamicFpsSaveMixin {
    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private static void arcadium$sharedIdleSettings(CallbackInfo ci) { if (ModernIdle.interceptSave()) ci.cancel(); }
}
