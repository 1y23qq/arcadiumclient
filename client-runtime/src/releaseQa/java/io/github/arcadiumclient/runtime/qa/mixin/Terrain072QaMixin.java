// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.client.optimizations.ResidentPlayer072NativeQa;
import io.github.arcadiumclient.runtime.client.optimizations.TerrainCommands072NativeQa;
import io.github.arcadiumclient.runtime.qa.TerrainScene072Qa;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class Terrain072QaMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void arcadium$terrainAndResidentQa(CallbackInfo callback) {
        TerrainCommands072NativeQa.tick();
        ResidentPlayer072NativeQa.tick();
        TerrainScene072Qa.tick();
    }
    @Inject(method = "runGame", at = @At("TAIL"))
    private void arcadium$terrainSceneFrame(CallbackInfo callback) { TerrainScene072Qa.frame(); }
}
