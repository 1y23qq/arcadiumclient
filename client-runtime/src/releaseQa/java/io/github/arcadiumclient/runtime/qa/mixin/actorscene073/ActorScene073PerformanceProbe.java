package io.github.arcadiumclient.runtime.qa.mixin.actorscene073;
import io.github.arcadiumclient.runtime.client.optimizations.ActorScene073PerformanceEvidence;
import io.github.arcadiumclient.runtime.qa.PlayerPipeline072Qa;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=PlayerPipeline072Qa.class,remap=false)
public abstract class ActorScene073PerformanceProbe {
    @Inject(method="armCounters",at=@At("TAIL"),remap=false)
    private static void actorScene$begin(CallbackInfo callback){ActorScene073PerformanceEvidence.begin();}
    @Inject(method="finish",at=@At("HEAD"),remap=false)
    private static void actorScene$finish(Throwable failure,CallbackInfo callback){ActorScene073PerformanceEvidence.finish(failure);}
}
