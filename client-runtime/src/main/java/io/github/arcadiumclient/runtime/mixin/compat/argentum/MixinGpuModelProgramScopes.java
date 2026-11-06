// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.EntityProgramScopes;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@IfModLoaded("argentum")
@Mixin(value = GlProgram.class, remap = false)
public abstract class MixinGpuModelProgramScopes {
    @Unique private long[] arcadium$programTokens;
    @Unique private int arcadium$programDepth;

    @Inject(method = "bind", at = @At("TAIL"), remap = false)
    private void arcadium$beginScope(CallbackInfo callback) {
        if (arcadium$programDepth == 16) throw new IllegalStateException("Entity program bind nesting exceeded");
        long token = EntityProgramScopes.begin(((GlProgram<?>)(Object)this).handle());
        if (token == 0 && arcadium$programDepth == 0) return;
        if (arcadium$programTokens == null) arcadium$programTokens = new long[16];
        arcadium$programTokens[arcadium$programDepth++] = token;
    }

    @Inject(method = "unbind", at = @At("HEAD"), remap = false)
    private void arcadium$endScope(CallbackInfo callback) {
        if (arcadium$programDepth == 0) return;
        int at = arcadium$programDepth - 1;
        EntityProgramScopes.end(arcadium$programTokens[at], ((GlProgram<?>)(Object)this).handle());
        arcadium$programTokens[at] = 0; arcadium$programDepth--;
    }
}
