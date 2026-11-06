// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.HologramNameNative073Qa;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class HologramName073DriverMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void hologram073$tick(CallbackInfo callback) { if (HologramNameNative073Qa.enabled()) HologramNameNative073Qa.tick(); }
}
