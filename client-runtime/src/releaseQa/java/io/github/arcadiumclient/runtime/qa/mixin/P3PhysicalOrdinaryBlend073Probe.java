// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.P3PhysicalOrdinary073Evidence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="io.github.arcadiumclient.runtime.client.optimizations.GpuVoxelSkinBorrowedBlendState",remap=false)
public abstract class P3PhysicalOrdinaryBlend073Probe {
    @Inject(method="capture()J",at=@At("RETURN"),remap=false)
    private void p3$before(CallbackInfoReturnable<Long> info){P3PhysicalOrdinary073Evidence.auxiliary("auxiliary-before");}
    @Inject(method="restore(J)V",at=@At("HEAD"),remap=false)
    private void p3$after(long ticket,CallbackInfo info){P3PhysicalOrdinary073Evidence.auxiliary("auxiliary-after");}
    @Inject(method="restore(J)V",at=@At("RETURN"),remap=false)
    private void p3$restored(long ticket,CallbackInfo info){P3PhysicalOrdinary073Evidence.auxiliary("auxiliary-restored");}
}
