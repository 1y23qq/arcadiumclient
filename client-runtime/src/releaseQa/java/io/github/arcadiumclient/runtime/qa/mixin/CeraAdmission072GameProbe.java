// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import io.github.arcadiumclient.runtime.qa.CeraAdmission072Qa;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
public abstract class CeraAdmission072GameProbe {
    @Inject(method="tick",at=@At("TAIL")) private void cera$tick(CallbackInfo callback){CeraAdmission072Qa.tick();}
    @Inject(method="runGame",at=@At("TAIL")) private void cera$frame(CallbackInfo callback){CeraAdmission072Qa.frame();}
}
