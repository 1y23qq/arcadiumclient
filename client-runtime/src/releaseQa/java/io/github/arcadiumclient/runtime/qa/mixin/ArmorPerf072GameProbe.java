// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import io.github.arcadiumclient.runtime.qa.ArmorPerf072Qa;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
public abstract class ArmorPerf072GameProbe {
    @Inject(method="tick",at=@At("TAIL")) private void armor$tick(CallbackInfo callback){if(ArmorPerf072Qa.enabled())ArmorPerf072Qa.tick();}
    @Inject(method="runGame",at=@At("TAIL")) private void armor$frame(CallbackInfo callback){if(ArmorPerf072Qa.enabled())ArmorPerf072Qa.frame();}
}
