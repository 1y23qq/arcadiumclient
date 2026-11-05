// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.ModernArmorGlintPipeline;
import java.util.function.Function;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@IfModLoaded("argentum")
@Mixin(value = GlProgram.class, remap = false)
public abstract class MixinModernArmorProgram {
    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void arcadium$verifyModernProgram(int handle, Function<?, ?> factory, CallbackInfo callback) { ModernArmorGlintPipeline.programCreated(handle); }
    @Inject(method = "destroyInternal", at = @At("HEAD"), remap = false)
    private void arcadium$forgetModernProgram(CallbackInfo callback) { ModernArmorGlintPipeline.programDeleted(((GlProgram<?>)(Object)this).handle()); }
}
