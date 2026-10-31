package io.github.arcadiumclient.runtime.qa.mixin;
import io.github.arcadiumclient.runtime.qa.Features062Qa;
import io.github.arcadiumclient.runtime.client.features.SkinLayersBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(targets="dev.rdh.argentum.impl.render.entity.instancing.EntityCapture",remap=false,priority=500)
public class Skin062CaptureProbe {
    @Inject(method="record",at=@At("RETURN")) private void release$part(@Coerce Object part,float scale,CallbackInfoReturnable<Boolean> ci){
        if(!Features062Qa.enabled())return;if(ci.getReturnValue())Features062Qa.flatParts++;else if(SkinLayersBridge.hasMesh(part))Features062Qa.voxelParts++;
    }
}
