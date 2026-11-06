// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import io.github.arcadiumclient.runtime.qa.MetalClientComposition072Qa;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.tomgirl.pylon.window.DisplaySdl;
import pl.tomgirl.pylon.window.GpuSurface;
@Pseudo
@Mixin(value=DisplaySdl.class,remap=false)
public abstract class MetalClientComposition072SurfaceMixin {
    @Inject(method="create",at=@At("HEAD"),remap=false)
    private void metal072$surface(GpuSurface fallback,CallbackInfo callback){MetalClientComposition072Qa.beforeCreate((DisplaySdl)(Object)this,fallback);}
}
