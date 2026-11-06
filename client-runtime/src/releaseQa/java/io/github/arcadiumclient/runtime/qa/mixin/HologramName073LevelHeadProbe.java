// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.client.features.LevelHead;
import io.github.arcadiumclient.runtime.qa.HologramNameNative073Qa;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelHead.class, remap = false)
public abstract class HologramName073LevelHeadProbe {
    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private static void hologram073$levelHead(Entity entity, String name, double x, double y, double z, CallbackInfo callback) { HologramNameNative073Qa.noteLevelHead(); }
}
