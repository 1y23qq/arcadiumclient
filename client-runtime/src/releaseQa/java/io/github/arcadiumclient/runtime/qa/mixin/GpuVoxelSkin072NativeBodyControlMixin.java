// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.PlayerPipeline072Qa;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="io.github.arcadiumclient.runtime.client.optimizations.GpuPlayerPipeline",remap=false)
public abstract class GpuVoxelSkin072NativeBodyControlMixin {
    @Inject(method="capture",at=@At("HEAD"),cancellable=true,remap=false)
    private static void voxel072$nativeBody(PlayerRenderer renderer,ClientPlayerEntity player,double x,double y,double z,float delta,CallbackInfoReturnable<Boolean> callback){
        if(!Boolean.getBoolean("arcadium.playerPipeline072.nativeBodyControl")||!PlayerPipeline072Qa.enabled()
                ||!player.getClass().getName().equals("io.github.arcadiumclient.runtime.qa.PlayerPipeline072Qa$Actor"))return;
        Minecraft mc=Minecraft.getInstance();
        if(!System.getProperty("arcadium.playerPipeline072.mode","visual").equals("visual")
                ||!"ArcadiumTest".equals(mc.getSession().getUsername())||!"0".equals(mc.getSession().getAccessToken()))
            throw new IllegalStateException("Native body control requires the owned offline visual fixture");
        callback.setReturnValue(false);
    }
}
