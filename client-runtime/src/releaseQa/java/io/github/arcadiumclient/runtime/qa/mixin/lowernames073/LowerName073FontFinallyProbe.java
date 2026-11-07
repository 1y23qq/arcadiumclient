// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin.lowernames073;
import io.github.arcadiumclient.runtime.qa.HologramNameFont073Evidence;
import io.github.arcadiumclient.runtime.qa.lowernames073.LowerNameNative073Evidence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=HologramNameFont073Evidence.class,remap=false)
public abstract class LowerName073FontFinallyProbe {
    @Inject(method="endImage",at=@At("RETURN"),remap=false) private static void lower073$end(CallbackInfo c){LowerNameNative073Evidence.fontFinally();}
}
