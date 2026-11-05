// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.CeraAdmission072PoolEvidence;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="dev.rdh.argentum.api.render.instancing.TextureArrayManager$Pool",remap=false)
public abstract class CeraAdmission072PoolProbe {
    @Inject(method="getLayer",at=@At("HEAD"),remap=false)
    private void cera072$before(Identifier texture,@Coerce Object source,int sourceId,int generation,int frame,CallbackInfoReturnable<Object> callback){CeraAdmission072PoolEvidence.head(this,texture,source,sourceId,generation,frame);}
    @Inject(method="getLayer",at=@At("RETURN"),remap=false)
    private void cera072$after(Identifier texture,@Coerce Object source,int sourceId,int generation,int frame,CallbackInfoReturnable<Object> callback){CeraAdmission072PoolEvidence.returned(this,callback.getReturnValue());}
    @Inject(method="copy",at=@At("HEAD"),remap=false)
    private void cera072$copy(int source,int index,CallbackInfoReturnable<Boolean> callback){CeraAdmission072PoolEvidence.copied(this);}
}
