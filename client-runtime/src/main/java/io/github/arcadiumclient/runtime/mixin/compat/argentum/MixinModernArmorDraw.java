// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.ModernArmorGlintPipeline;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@IfModLoaded("argentum")
@Mixin(value = GlProgram.class, remap = false)
public abstract class MixinModernArmorDraw {
    @Unique private int[] arcadium$materialScopes;
    @Unique private int arcadium$materialDepth;

    @Inject(method = "bind", at = @At("TAIL"), remap = false)
    private void arcadium$bindMaterials(CallbackInfo callback) {
        int token = ModernArmorGlintPipeline.beginProgram(((GlProgram<?>)(Object)this).handle());
        if (token == 0 && arcadium$materialDepth == 0) return;
        if (arcadium$materialScopes == null) arcadium$materialScopes = new int[16];
        if (arcadium$materialDepth < arcadium$materialScopes.length) arcadium$materialScopes[arcadium$materialDepth] = token;
        arcadium$materialDepth++;
    }

    @Inject(method = "unbind", at = @At("HEAD"), remap = false)
    private void arcadium$restoreMaterials(CallbackInfo callback) {
        if (arcadium$materialDepth == 0) return;
        int at = --arcadium$materialDepth;
        if (at >= arcadium$materialScopes.length) return;
        int token = arcadium$materialScopes[at]; arcadium$materialScopes[at] = 0;
        ModernArmorGlintPipeline.endDraw(token);
    }
}
