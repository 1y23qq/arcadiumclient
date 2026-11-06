package io.github.arcadiumclient.runtime.qa.mixin.actorscene073;
import io.github.arcadiumclient.runtime.client.optimizations.ActorScene073MinecraftEvidence;
import io.github.arcadiumclient.runtime.client.optimizations.GpuPlayerPackets;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=GpuPlayerPackets.class,remap=false)
public abstract class ActorScene073PacketsMaterialProbe {
    @Inject(method="observeMaterial",at=@At("RETURN"),remap=false)
    private static void actorScene$material(int job,Object main,Object batch,Identifier texture,int layer,CallbackInfo callback){ActorScene073MinecraftEvidence.material(job);}
}
