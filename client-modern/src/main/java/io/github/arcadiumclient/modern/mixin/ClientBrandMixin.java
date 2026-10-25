// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import net.minecraft.client.ClientBrandRetriever;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientBrandRetriever.class)
public abstract class ClientBrandMixin {
    @Inject(method = "getClientModName", at = @At("RETURN"), cancellable = true)
    private static void arcadium$brand(CallbackInfoReturnable<String> cir) { cir.setReturnValue("Arcadium"); }
}
