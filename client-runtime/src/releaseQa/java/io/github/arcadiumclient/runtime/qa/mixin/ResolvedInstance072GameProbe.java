// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import io.github.arcadiumclient.runtime.qa.ResolvedInstance072Qa;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
public abstract class ResolvedInstance072GameProbe {
    @Inject(method = "tick", at = @At("TAIL"))
    private void resolved$tick(CallbackInfo callback) { if (ResolvedInstance072Qa.enabled()) ResolvedInstance072Qa.tick(); }
    @Inject(method = "runGame", at = @At("TAIL"))
    private void resolved$frame(CallbackInfo callback) { if (ResolvedInstance072Qa.enabled()) ResolvedInstance072Qa.frame(); }
}
