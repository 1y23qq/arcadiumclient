// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin.fusedminecraft073;
import io.github.arcadiumclient.runtime.client.optimizations.FusedMinecraft073Evidence;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo @Mixin(targets="io.github.arcadiumclient.runtime.client.optimizations.ActorFusedRuntime$Fence",remap=false)
public abstract class FusedMinecraft073FenceProbe {
 @Inject(method="<init>",at=@At("RETURN"),remap=false)
 private void fused073$created(CallbackInfo callback){FusedMinecraft073Evidence.fenceCreated(this);}
 @Inject(method="delete",at=@At("RETURN"),remap=false)
 private void fused073$deleted(CallbackInfo callback){FusedMinecraft073Evidence.fenceDeleted(this);}
}
