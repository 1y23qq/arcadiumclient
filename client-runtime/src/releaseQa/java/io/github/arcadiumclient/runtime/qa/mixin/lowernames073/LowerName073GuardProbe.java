// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin.lowernames073;
import io.github.arcadiumclient.runtime.client.optimizations.LowerNamePreparation;
import io.github.arcadiumclient.runtime.qa.lowernames073.LowerNameNative073Evidence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value=LowerNamePreparation.class,remap=false)
public abstract class LowerName073GuardProbe {
    @Inject(method="active",at=@At("RETURN"),remap=false) private static void lower073$active(Object owner,CallbackInfoReturnable<Boolean> c){LowerNameNative073Evidence.active(owner,c.getReturnValue());}
    @Inject(method="noteTyped",at=@At("HEAD"),remap=false) private static void lower073$noteHead(CallbackInfo c){LowerNameNative073Evidence.note(true);}
    @Inject(method="noteTyped",at=@At("RETURN"),remap=false) private static void lower073$noteReturn(CallbackInfo c){LowerNameNative073Evidence.note(false);}
}
