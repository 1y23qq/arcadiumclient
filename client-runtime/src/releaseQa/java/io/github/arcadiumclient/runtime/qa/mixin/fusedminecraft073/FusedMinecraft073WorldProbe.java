// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin.fusedminecraft073;
import io.github.arcadiumclient.runtime.client.optimizations.FusedMinecraft073Evidence;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=GameRenderer.class,priority=2300)
public abstract class FusedMinecraft073WorldProbe {
 @Inject(method="render(IFJ)V",at=@At(value="INVOKE_STRING",target="Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V",args="ldc=hand",shift=At.Shift.BEFORE))
 private void fused073$preBlur(int pass,float delta,long deadline,CallbackInfo callback){FusedMinecraft073Evidence.captureWorld(pass,delta);}
}
