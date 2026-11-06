// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.TerrainSceneSetup072Qa;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class TerrainSceneSetup072Mixin {
    @Inject(method="tick",at=@At("TAIL"))
    private void terrainWorld072$tick(CallbackInfo callback){TerrainSceneSetup072Qa.tick();}
    @Inject(method="runGame",at=@At("TAIL"))
    private void terrainWorld072$frame(CallbackInfo callback){TerrainSceneSetup072Qa.frame();}
}
