// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import io.github.arcadiumclient.runtime.qa.PlayerFeatures073Qa;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
public abstract class PlayerFeatures073GameProbe {
    @Inject(method="tick",at=@At("TAIL")) private void features073$tick(CallbackInfo callback){PlayerFeatures073Qa.tick();}
    @Inject(method="runGame",at=@At("TAIL")) private void features073$frame(CallbackInfo callback){PlayerFeatures073Qa.frame();}
}
