package io.github.arcadiumclient.runtime.qa.mixin.actorscene073;
import io.github.arcadiumclient.runtime.client.optimizations.ActorScene073MinecraftEvidence;
import io.github.arcadiumclient.runtime.qa.PlayerPipeline072Qa;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=PlayerPipeline072Qa.class,remap=false)
public abstract class ActorScene073CaptureProbe {
    @Inject(method="beginWorld",at=@At("HEAD"),remap=false)
    private static void actorScene$world(CallbackInfo callback){ActorScene073MinecraftEvidence.beginWorld();}
    @Inject(method="capture",at=@At("HEAD"),remap=false)
    private static void actorScene$frame(Minecraft mc,String filename,CallbackInfo callback)throws Exception{ActorScene073MinecraftEvidence.capture(mc,filename);}
    @Inject(method="finish",at=@At("HEAD"),remap=false)
    private static void actorScene$finish(Throwable failure,CallbackInfo callback){ActorScene073MinecraftEvidence.finish(failure);}
}
