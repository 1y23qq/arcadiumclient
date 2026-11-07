// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin.fusedminecraft073;
import io.github.arcadiumclient.runtime.client.optimizations.FusedMinecraft073Evidence;
import io.github.arcadiumclient.runtime.client.optimizations.GpuPlayerPipeline;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=GpuPlayerPipeline.class,remap=false)
public abstract class FusedMinecraft073PipelineProbe {
 @Inject(method="capture",at=@At("RETURN"),remap=false)
 private static void fused073$pipeline(PlayerRenderer renderer,ClientPlayerEntity player,double x,double y,double z,float delta,CallbackInfoReturnable<Boolean> callback){FusedMinecraft073Evidence.pipeline(callback.getReturnValueZ());}
}
