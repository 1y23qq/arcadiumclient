// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import io.github.arcadiumclient.runtime.qa.CeraAdmission072Qa;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="io.github.arcadiumclient.runtime.client.optimizations.GpuVoxelSkinRenderer",remap=false)
public abstract class CeraAdmission072PreparationProbe {
    @Inject(method="prepareTexture",at=@At("HEAD"),remap=false,require=0)
    private void cera$prepare(@Coerce Object state,Object main,Identifier texture,Object fact,CallbackInfoReturnable<Boolean> callback){CeraAdmission072Qa.beginPreparation(state);}
    @Inject(method="prepareTexture",at=@At("RETURN"),remap=false,require=0)
    private void cera$prepared(@Coerce Object state,Object main,Identifier texture,Object fact,CallbackInfoReturnable<Boolean> callback){CeraAdmission072Qa.endPreparation(texture,fact,callback.getReturnValueZ());}
}
