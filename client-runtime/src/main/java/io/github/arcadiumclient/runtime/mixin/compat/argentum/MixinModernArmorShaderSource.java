// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.ModernGlintShaderPatch;
import io.github.arcadiumclient.runtime.client.optimizations.ModernArmorGlintPipeline;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL43;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@IfModLoaded("argentum")
@Mixin(targets = "org.embeddedt.embeddium.impl.render.shader.ShaderLoader", remap = false)
public abstract class MixinModernArmorShaderSource {
    @Unique private static final ThreadLocal<Boolean> arcadium$readingPeer = ThreadLocal.withInitial(() -> false);
    @Shadow public static String getShaderSource(String name) { throw new AssertionError(); }

    @Inject(method = "getShaderSource", at = @At("RETURN"), cancellable = true, remap = false)
    private static void arcadium$modernShader(String name, CallbackInfoReturnable<String> callback) {
        if (!ModernArmorGlintPipeline.shaderAdapterEnabled()) return;
        String peer = ModernGlintShaderPatch.peer(name);
        if (peer == null || arcadium$readingPeer.get()) return;
        boolean supported;
        try {
            boolean gl43 = GL.getCapabilities().OpenGL43;
            supported = ModernGlintShaderPatch.supportedCapabilities(gl43,
                    gl43 ? GL11.glGetInteger(GL43.GL_MAX_VERTEX_SHADER_STORAGE_BLOCKS) : 0,
                    gl43 ? GL11.glGetInteger(GL43.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS) : 0);
        } catch (IllegalStateException missingContext) { supported = false; }
        if (!supported) return;
        arcadium$readingPeer.set(true);
        try { callback.setReturnValue(ModernGlintShaderPatch.patch(name, callback.getReturnValue(), getShaderSource(peer), true, true)); }
        finally { arcadium$readingPeer.remove(); }
    }
}
