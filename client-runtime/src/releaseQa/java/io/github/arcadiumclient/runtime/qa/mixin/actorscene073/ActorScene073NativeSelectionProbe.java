package io.github.arcadiumclient.runtime.qa.mixin.actorscene073;
import io.github.arcadiumclient.runtime.client.optimizations.ActorScene073MinecraftEvidence;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="dev.rdh.argentum.impl.render.entity.instancing.ModelInstancer",remap=false)
public abstract class ActorScene073NativeSelectionProbe {
    @Inject(method="selectTexture",at=@At("RETURN"),remap=false)
    private void actorScene$selected(Identifier texture,@Coerce Object pass,CallbackInfoReturnable<Object> callback){ActorScene073MinecraftEvidence.selected(this,texture,callback.getReturnValue());}
}
