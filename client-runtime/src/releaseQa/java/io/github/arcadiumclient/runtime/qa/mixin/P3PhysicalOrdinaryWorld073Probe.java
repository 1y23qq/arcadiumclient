// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import io.github.arcadiumclient.runtime.qa.P3PhysicalOrdinary073Evidence;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRenderer.class)
public abstract class P3PhysicalOrdinaryWorld073Probe {
    @Inject(method="render(IFJ)V",at=@At(value="INVOKE_STRING",target="Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V",args="ldc=hand",shift=At.Shift.BEFORE))
    private void p3$world(int pass,float partial,long deadline,CallbackInfo info){P3PhysicalOrdinary073Evidence.captureWorld(pass,partial);}
}
