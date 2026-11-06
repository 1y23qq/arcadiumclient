package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.TerrainResidentBoot072Qa;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class TerrainResidentBoot072Probe {
    @Inject(method="tick",at=@At("TAIL"))
    private void terrainBoot072$titleProof(CallbackInfo callback){TerrainResidentBoot072Qa.tick();}
}
