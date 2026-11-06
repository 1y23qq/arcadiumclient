// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import io.github.arcadiumclient.runtime.qa.MetalClientComposition072Qa;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="org.polyfrost.oneconfig.internal.ui.compose.SkiaCtx",remap=false)
public abstract class MetalClientComposition072ComposeProbe {
    @Inject(method={"draw","flushToTarget"},at=@At(value="INVOKE",target="Lorg/jetbrains/skia/DirectContext;flush()Lorg/jetbrains/skia/DirectContext;",shift=At.Shift.AFTER),remap=false)
    private void metal072$actualSkiko(CallbackInfo callback){MetalClientComposition072Qa.skikoFlush();}
    @Inject(method="submitComposeFrame",at=@At("RETURN"),remap=false)
    private void metal072$composeSubmit(boolean dirty,Runnable draw,CallbackInfo callback){MetalClientComposition072Qa.composeSubmit();}
}
