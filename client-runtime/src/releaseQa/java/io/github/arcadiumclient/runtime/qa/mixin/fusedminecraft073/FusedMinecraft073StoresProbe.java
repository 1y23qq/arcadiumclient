// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin.fusedminecraft073;
import io.github.arcadiumclient.runtime.client.optimizations.FusedMinecraft073Evidence;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Pseudo @Mixin(targets="io.github.arcadiumclient.runtime.client.optimizations.ActorFusedRuntime$Stores",remap=false)
public abstract class FusedMinecraft073StoresProbe {
 @Inject(method="create",at=@At("RETURN"),remap=false)
 private void fused073$created(int words,CallbackInfoReturnable<Integer> callback){FusedMinecraft073Evidence.storeCreated(callback.getReturnValueI());}
 @Inject(method="delete",at=@At("RETURN"),remap=false)
 private void fused073$deleted(int name,CallbackInfo callback){FusedMinecraft073Evidence.storeDeleted(name);}
}
