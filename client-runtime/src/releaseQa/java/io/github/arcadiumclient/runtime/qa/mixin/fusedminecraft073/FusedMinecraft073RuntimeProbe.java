// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin.fusedminecraft073;
import io.github.arcadiumclient.runtime.client.optimizations.FusedMinecraft073Evidence;
import java.nio.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo @Mixin(targets="io.github.arcadiumclient.runtime.client.optimizations.ActorFusedRuntime",remap=false)
public abstract class FusedMinecraft073RuntimeProbe {
 @Inject(method="begin",at=@At("HEAD"),remap=false)
 private static void fused073$raw(int program,Object link,@Coerce Object raw,FloatBuffer ordinary,int rows,long revision,IntBuffer payload,CallbackInfoReturnable<Object> callback){FusedMinecraft073Evidence.naturalRaw(raw,rows);}
 @Inject(method="begin",at=@At("RETURN"),remap=false)
 private static void fused073$scope(int program,Object link,@Coerce Object raw,FloatBuffer ordinary,int rows,long revision,IntBuffer payload,CallbackInfoReturnable<Object> callback){FusedMinecraft073Evidence.naturalScope(callback.getReturnValue());}
 @Inject(method="prepareFrame",at=@At("RETURN"),remap=false)
 private static void fused073$prepared(CallbackInfoReturnable<Boolean> callback){FusedMinecraft073Evidence.prepared();}
}
