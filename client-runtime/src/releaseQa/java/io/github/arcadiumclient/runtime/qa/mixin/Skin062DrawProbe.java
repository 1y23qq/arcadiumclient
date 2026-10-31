package io.github.arcadiumclient.runtime.qa.mixin;
import io.github.arcadiumclient.runtime.qa.Features062Qa;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="org.polyfrost.skinlayers.render.CompiledMesh",remap=false)
public class Skin062DrawProbe {
    @Inject(method="render",at=@At("HEAD")) private void release$draw(float scale,CallbackInfo ci){if(Features062Qa.enabled())Features062Qa.voxelDraws++;}
    @Inject(method="compile",at=@At("HEAD")) private void release$compile(float scale,CallbackInfo ci){if(Features062Qa.enabled())Features062Qa.meshCompiles++;}
}
