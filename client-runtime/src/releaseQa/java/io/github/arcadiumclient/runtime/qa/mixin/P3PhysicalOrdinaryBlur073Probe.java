// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import io.github.arcadiumclient.runtime.qa.P3PhysicalOrdinary073Evidence;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="org.polyfrost.polyblur.client.blur.motion.MotionBlur",remap=false)
public abstract class P3PhysicalOrdinaryBlur073Probe {
    @Inject(method="render",at=@At("HEAD"),remap=false)
    private static void p3$before(@Coerce Object target,CallbackInfo info){P3PhysicalOrdinary073Evidence.blur(false);}
    @Inject(method="render",at=@At("RETURN"),remap=false)
    private static void p3$after(@Coerce Object target,CallbackInfo info){P3PhysicalOrdinary073Evidence.blur(true);}
}
