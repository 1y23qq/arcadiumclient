// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin.fusedminecraft073;
import io.github.arcadiumclient.runtime.client.optimizations.FusedMinecraft073Evidence;
import io.github.arcadiumclient.runtime.qa.PlayerPipeline072Qa;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=PlayerPipeline072Qa.class,remap=false)
public abstract class FusedMinecraft073CaptureProbe {
 @Inject(method="beginWorld",at=@At("HEAD"),remap=false) private static void fused073$entry(CallbackInfo callback){FusedMinecraft073Evidence.beginWorld();}
 @Inject(method="worldRendered",at=@At("HEAD"),remap=false) private static void fused073$returned(CallbackInfo callback){FusedMinecraft073Evidence.worldReturned();}
 @Inject(method="capture",at=@At("HEAD"),remap=false) private static void fused073$capture(Minecraft mc,String name,CallbackInfo callback){FusedMinecraft073Evidence.capture(mc,name);}
 @Inject(method="finish",at=@At("HEAD"),remap=false) private static void fused073$finish(Throwable failure,CallbackInfo callback){FusedMinecraft073Evidence.finish(failure);}
}
