// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.ResidentBody072LifecycleEvidence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="io.github.arcadiumclient.runtime.client.optimizations.GpuResidentBodies",remap=false)
public abstract class ResidentBody072LifecycleProbe {
    @Inject(method="reset",at=@At("HEAD"),remap=false)
    private static void body072$beforeReset(CallbackInfo callback){ResidentBody072LifecycleEvidence.before("reset",null);}
    @Inject(method="reset",at=@At("RETURN"),remap=false)
    private static void body072$afterReset(CallbackInfo callback){ResidentBody072LifecycleEvidence.after("reset",null);}
    @Inject(method="beforeMainClose",at=@At("HEAD"),remap=false)
    private static void body072$beforeClose(Object main,Object commands,CallbackInfo callback){ResidentBody072LifecycleEvidence.before("main-close",main);}
    @Inject(method="beforeMainClose",at=@At("RETURN"),remap=false)
    private static void body072$afterClose(Object main,Object commands,CallbackInfo callback){ResidentBody072LifecycleEvidence.after("main-close",main);}
}
