package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import io.github.arcadiumclient.runtime.client.optimizations.ActorRoot073;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinGpuModelActorRootTick073 {
    @Inject(method="tick()V",at=@At("TAIL"),require=1)
    private void arcadium$rootTick(CallbackInfo callback){ActorRoot073.tick();}
}
