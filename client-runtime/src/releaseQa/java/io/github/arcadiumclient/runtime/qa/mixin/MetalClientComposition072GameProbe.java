// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import io.github.arcadiumclient.runtime.qa.MetalClientComposition072Qa;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
public abstract class MetalClientComposition072GameProbe {
    @Inject(method="init",at=@At("TAIL")) private void metal072$init(CallbackInfo callback){MetalClientComposition072Qa.initialized();}
    @Inject(method="tick",at=@At("TAIL")) private void metal072$tick(CallbackInfo callback){MetalClientComposition072Qa.tick();}
    @Inject(method="runGame",at=@At("TAIL")) private void metal072$frame(CallbackInfo callback){MetalClientComposition072Qa.frame();}
    @Inject(method="stop",at=@At("HEAD")) private void metal072$stop(CallbackInfo callback){MetalClientComposition072Qa.stopping();}
}
