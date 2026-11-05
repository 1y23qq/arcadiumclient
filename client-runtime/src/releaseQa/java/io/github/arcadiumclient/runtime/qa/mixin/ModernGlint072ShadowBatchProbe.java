// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.qa.ModernGlintPixelTrace072Qa;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="dev.rdh.argentum.impl.render.entity.EntityShadowBatch",remap=false)
public abstract class ModernGlint072ShadowBatchProbe {
    @WrapMethod(method="flush")
    private void modern$flush(CommandList commands,Operation<Void> original){
        int token=ModernGlintPixelTrace072Qa.beginShadowBatch(this);
        try{original.call(commands);}finally{ModernGlintPixelTrace072Qa.endShadowBatch(token);}
    }

    @Inject(method="record",at=@At("RETURN"),remap=false)
    private void modern$record(@Coerce Object world,@Coerce Object entity,double x,double y,double z,float first,float second,float third,CallbackInfoReturnable<Boolean> callback){
        ModernGlintPixelTrace072Qa.shadowRecord(this,world,entity,x,y,z,first,second,third,callback.getReturnValueZ());
    }
}
