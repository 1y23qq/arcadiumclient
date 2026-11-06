package io.github.arcadiumclient.runtime.qa.mixin.actorscene073;
import io.github.arcadiumclient.runtime.client.optimizations.ActorScene073MinecraftEvidence;
import io.github.arcadiumclient.runtime.client.optimizations.GpuPlayerPipeline;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(value=GpuPlayerPipeline.class,remap=false)
public abstract class ActorScene073PipelineProbe {
    @WrapMethod(method="capture",remap=false)
    private static boolean actorScene$player(PlayerRenderer renderer,ClientPlayerEntity player,double x,double y,double z,float partial,Operation<Boolean> original){
        int token=ActorScene073MinecraftEvidence.enter(player);boolean accepted=false;
        try{return accepted=original.call(renderer,player,x,y,z,partial);}finally{ActorScene073MinecraftEvidence.leave(token,accepted);}
    }
}
