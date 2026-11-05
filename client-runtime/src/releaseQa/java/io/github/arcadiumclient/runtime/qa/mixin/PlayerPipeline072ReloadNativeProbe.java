// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.arcadiumclient.runtime.qa.PlayerPipeline072ReloadProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.rdh.argentum.impl.render.entity.instancing.ModelInstancer", remap = false)
public abstract class PlayerPipeline072ReloadNativeProbe {
    @Inject(method = "flush", at = @At("RETURN"))
    private void arcadium$recordActualReloadDraws(CallbackInfoReturnable<Object> callback) { PlayerPipeline072ReloadProbe.flushed(this, callback.getReturnValue()); }
    @WrapOperation(method = "flush", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Throwable;)V"))
    private void arcadium$observeNativeReloadFailure(@Coerce Object logger, String message, Throwable error, Operation<Void> original) {
        PlayerPipeline072ReloadProbe.nativeFailure(this, error); original.call(logger, message, error);
    }
}
