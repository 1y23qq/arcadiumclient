package io.github.arcadiumclient.runtime.qa.mixin.actorscene073;
import io.github.arcadiumclient.runtime.client.optimizations.ActorScene073MinecraftEvidence;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.nio.FloatBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(targets="io.github.arcadiumclient.runtime.client.optimizations.ActorSceneGpu",remap=false)
public abstract class ActorScene073ResolverProbe {
    @Inject(method="bind",at=@At("HEAD"),remap=false)
    private static void actorScene$beforeResolve(@Coerce Object scene,FloatBuffer legacy,int rows,long batch,long revision,CallbackInfoReturnable<Boolean> callback){ActorScene073MinecraftEvidence.resolveBegin();}
    @Inject(method="bind",at=@At("RETURN"),remap=false)
    private static void actorScene$resolved(@Coerce Object scene,FloatBuffer legacy,int rows,long batch,long revision,CallbackInfoReturnable<Boolean> callback){ActorScene073MinecraftEvidence.resolveEnd(scene,rows,batch,callback.getReturnValue());}
    @WrapMethod(method="close",remap=false)
    private static void actorScene$closed(Operation<Void> original){Object owned=ActorScene073MinecraftEvidence.closing();try{original.call();}finally{ActorScene073MinecraftEvidence.closed(owned);}}
}
