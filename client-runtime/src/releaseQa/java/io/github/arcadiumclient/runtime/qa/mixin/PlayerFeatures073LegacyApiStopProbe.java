// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import io.github.arcadiumclient.runtime.qa.PlayerFeatures073LegacyApiOracle;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
public abstract class PlayerFeatures073LegacyApiStopProbe {
    @Inject(method="stop()V",at=@At("HEAD"))
    private void features073$preserve(CallbackInfo callback){PlayerFeatures073LegacyApiOracle.finish();}
}
