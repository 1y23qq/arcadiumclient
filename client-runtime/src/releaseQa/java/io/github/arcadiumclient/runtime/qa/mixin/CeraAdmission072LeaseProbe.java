// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import io.github.arcadiumclient.runtime.qa.CeraAdmission072LeaseEvidence;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Pseudo
@Mixin(targets="io.github.arcadiumclient.runtime.client.optimizations.GpuVoxelSkinPreflight",remap=false)
public abstract class CeraAdmission072LeaseProbe {
    @Inject(method="arm",at=@At("RETURN"),remap=false)
    private void cera$armed(Object main,Object parts,int mask,Object texture,int source,int variant,long epoch,Object fact,CallbackInfo callback){CeraAdmission072LeaseEvidence.arm(this,main,parts,mask,texture,source,variant,epoch,fact);}
    @Inject(method="consume",at=@At("HEAD"),remap=false)
    private void cera$using(Object main,Object parts,int mask,Object texture,int source,long epoch,CallbackInfoReturnable<Integer> callback){CeraAdmission072LeaseEvidence.consume(this,main,parts,mask,texture,source,epoch);}
    @Inject(method="consume",at=@At("RETURN"),remap=false)
    private void cera$used(Object main,Object parts,int mask,Object texture,int source,long epoch,CallbackInfoReturnable<Integer> callback){CeraAdmission072LeaseEvidence.returned(this,callback.getReturnValueI());}
}
