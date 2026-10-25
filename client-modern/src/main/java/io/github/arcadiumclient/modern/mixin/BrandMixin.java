// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import io.github.arcadiumclient.modern.ClientBuild;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class BrandMixin {
    @Inject(method = "createTitle", at = @At("RETURN"), cancellable = true)
    private void arcadium$title(CallbackInfoReturnable<String> cir) { cir.setReturnValue("Arcadium " + ClientBuild.INSTANCE.getVERSION() + " | Minecraft 1.21.11"); }
}
