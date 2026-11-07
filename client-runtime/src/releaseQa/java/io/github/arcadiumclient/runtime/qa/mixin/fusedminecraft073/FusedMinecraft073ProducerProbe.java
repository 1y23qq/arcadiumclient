// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin.fusedminecraft073;
import io.github.arcadiumclient.runtime.client.optimizations.FusedMinecraft073Evidence;
import io.github.arcadiumclient.runtime.client.optimizations.GpuPlayerPackets;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=GpuPlayerPackets.class,remap=false)
public abstract class FusedMinecraft073ProducerProbe {
 @Inject(method="addActorScene",at=@At("RETURN"),remap=false)
 private static void fused073$scene(Object owner,Matrix4fc root,float swing,int left,int right,boolean sneak,boolean riding,boolean bow,int template,CallbackInfoReturnable<Integer> callback){FusedMinecraft073Evidence.producer(owner,callback.getReturnValueI(),true);}
 @Inject(method="addActor",at=@At("RETURN"),remap=false)
 private static void fused073$ordinary(Object owner,Matrix4fc root,float walk,float speed,float age,float yaw,float pitch,float swing,int left,int right,boolean sneak,boolean riding,boolean bow,int template,CallbackInfoReturnable<Integer> callback){FusedMinecraft073Evidence.producer(owner,callback.getReturnValueI(),false);}
}
