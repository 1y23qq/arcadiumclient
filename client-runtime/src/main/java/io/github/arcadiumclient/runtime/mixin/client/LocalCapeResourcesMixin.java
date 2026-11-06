// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client;

import io.github.arcadiumclient.runtime.client.capes.LocalCape;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class LocalCapeResourcesMixin {
    @Inject(method = "reloadResources()V", at = @At("HEAD"))
    private void arcadium$reloadLocalCape(CallbackInfo callback) {
        LocalCape.resourcesReloaded();
    }
}
