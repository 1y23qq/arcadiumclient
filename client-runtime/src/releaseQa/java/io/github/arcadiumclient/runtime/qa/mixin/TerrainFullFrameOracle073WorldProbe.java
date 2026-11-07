// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.qa.TerrainFullFrameOracle073;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=GameRenderer.class,priority=2300)
public abstract class TerrainFullFrameOracle073WorldProbe {
    @WrapMethod(method="renderWorld(FJ)V")
    private void terrain073$world(float partial,long deadline,Operation<Void> original){TerrainFullFrameOracle073.render(partial,deadline,original);}
    @Inject(method="render(IFJ)V",at=@At(value="INVOKE_STRING",target="Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V",args="ldc=hand",shift=At.Shift.BEFORE))
    private void terrain073$preBlur(int pass,float partial,long deadline,CallbackInfo callback){TerrainFullFrameOracle073.captureWorld(pass,partial);}
}
