package io.github.arcadiumclient.runtime.qa.mixin.actorscene073;

import io.github.arcadiumclient.runtime.client.optimizations.ActorFused073PerformanceEvidence;
import io.github.arcadiumclient.runtime.qa.PlayerPipeline072Qa;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=PlayerPipeline072Qa.class,remap=false)
public abstract class ActorFused073PerformanceProbe {
    @Inject(method="armCounters",at=@At("TAIL"),remap=false)
    private static void fused073$begin(CallbackInfo callback){ActorFused073PerformanceEvidence.begin();}
    @Inject(method="finish",at=@At("HEAD"),remap=false)
    private static void fused073$end(Throwable failure,CallbackInfo callback){ActorFused073PerformanceEvidence.finish(failure);}
}
