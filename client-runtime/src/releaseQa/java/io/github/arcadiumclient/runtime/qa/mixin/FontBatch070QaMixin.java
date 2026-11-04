// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.FontBatch070Qa;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class FontBatch070QaMixin {
    @Inject(method="tick",at=@At("TAIL"))
    private void arcadium$fontTestTick(CallbackInfo callback){FontBatch070Qa.tick();}
    @Inject(method="runGame",at=@At("TAIL"))
    private void arcadium$fontTestFrame(CallbackInfo callback){FontBatch070Qa.frame();}
}
