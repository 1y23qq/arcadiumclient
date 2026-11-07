// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin.fusedminecraft073;
import io.github.arcadiumclient.runtime.client.optimizations.FusedMinecraft073Evidence;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo @Mixin(targets="org.polyfrost.polyblur.client.blur.motion.MotionBlur",remap=false)
public abstract class FusedMinecraft073BlurProbe {
 @Inject(method="render",at=@At("HEAD"),remap=false) private static void fused073$entry(@Coerce Object target,CallbackInfo callback){FusedMinecraft073Evidence.blurEnter();}
 @Inject(method="render",at=@At("RETURN"),remap=false) private static void fused073$returned(@Coerce Object target,CallbackInfo callback){FusedMinecraft073Evidence.blurExit();}
}
