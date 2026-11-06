// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.UrgentStar072Qa;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class UrgentStar072GameProbe {
    @Inject(method="tick",at=@At("TAIL"))
    private void star072$tick(CallbackInfo callback){UrgentStar072Qa.tick();}
    @Inject(method="runGame",at=@At("TAIL"))
    private void star072$frame(CallbackInfo callback){UrgentStar072Qa.frame();}
}
