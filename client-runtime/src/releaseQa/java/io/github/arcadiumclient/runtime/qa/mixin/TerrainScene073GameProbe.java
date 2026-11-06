// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.TerrainScene073Qa;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class TerrainScene073GameProbe {
    @Inject(method="tick",at=@At("TAIL"))
    private void terrain073$tick(CallbackInfo callback){TerrainScene073Qa.tick();}
    @Inject(method="runGame",at=@At("TAIL"))
    private void terrain073$frame(CallbackInfo callback){TerrainScene073Qa.frame();}
}
