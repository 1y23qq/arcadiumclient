// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.gui.qa.mixin;

import io.github.arcadiumclient.modern.gui.qa.SharedOptionsQa;
import net.minecraft.client.Options;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Options.class)
abstract class SharedOptionsQaMixin {
    @Inject(method = "dataFix", at = @At("RETURN"))
    private void arcadium$fixed(CompoundTag input, CallbackInfoReturnable<CompoundTag> cir) { SharedOptionsQa.fixed(cir.getReturnValue()); }

    @Inject(method = "load", at = @At("RETURN"))
    private void arcadium$loaded(CallbackInfo ci) { SharedOptionsQa.loaded((Options) (Object) this); }
}
