// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.client.branding.NametagLayout;
import io.github.arcadiumclient.runtime.qa.HologramNameNative073Qa;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = NametagLayout.class, remap = false)
public abstract class HologramName073LayoutProbe {
    @Inject(method = "begin", at = @At("HEAD"), remap = false)
    private static void hologram073$begin(Entity entity, String label, CallbackInfoReturnable<NametagLayout.Scope> callback) { HologramNameNative073Qa.noteBegin(entity, label); }
    @Inject(method = "end", at = @At("HEAD"), remap = false)
    private static void hologram073$end(NametagLayout.Scope previous, CallbackInfo callback) { HologramNameNative073Qa.noteEnd(); }
    @Inject(method = "display", at = @At("HEAD"), remap = false)
    private static void hologram073$display(String label, CallbackInfoReturnable<String> callback) { HologramNameNative073Qa.noteDisplay(); }
}
