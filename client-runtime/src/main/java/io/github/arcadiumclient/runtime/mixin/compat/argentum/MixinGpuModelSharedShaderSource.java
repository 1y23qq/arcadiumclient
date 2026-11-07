// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.EntityInstancingShaderPatch;
import io.github.arcadiumclient.runtime.client.optimizations.GpuResidentBodyShaders;
import io.github.arcadiumclient.runtime.client.optimizations.ModernArmorGlintPipeline;
import io.github.arcadiumclient.runtime.client.optimizations.ModernGlintShaderPatch;
import io.github.arcadiumclient.runtime.client.optimizations.GpuVoxelSkinPhysicalProjection;
import io.github.arcadiumclient.runtime.client.optimizations.GpuVoxelSkinProjectionShaderPatch;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL43;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@IfModLoaded("argentum")
@Mixin(targets = "org.embeddedt.embeddium.impl.render.shader.ShaderLoader", remap = false)
public abstract class MixinGpuModelSharedShaderSource {
    @Unique private static final ThreadLocal<Boolean> arcadium$readingPeer = ThreadLocal.withInitial(() -> false);
    @Shadow public static String getShaderSource(String name) { throw new AssertionError(); }

    @Inject(method = "getShaderSource", at = @At("RETURN"), cancellable = true, remap = false)
    private static void arcadium$sharedShader(String name, CallbackInfoReturnable<String> callback) {
        if(GpuVoxelSkinPhysicalProjection.compiling()){
            if(arcadium$readingPeer.get())return;
            String peer=ModernGlintShaderPatch.peer(name);if(peer==null)return;
            arcadium$readingPeer.set(true);
            try{callback.setReturnValue(GpuVoxelSkinProjectionShaderPatch.patch(name,callback.getReturnValue(),getShaderSource(peer)));}
            finally{arcadium$readingPeer.remove();}
            return;
        }
        boolean body = GpuResidentBodyShaders.enabled(), modern = ModernArmorGlintPipeline.shaderAdapterEnabled();
        if ((!body && !modern) || arcadium$readingPeer.get()) return;
        String peer = ModernGlintShaderPatch.peer(name);
        if (peer == null) return;
        try {
            if (!GL.getCapabilities().OpenGL43) return;
            int blocks = GL11.glGetInteger(GL43.GL_MAX_VERTEX_SHADER_STORAGE_BLOCKS);
            int bindings = GL11.glGetInteger(GL43.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS);
            arcadium$readingPeer.set(true);
            try { callback.setReturnValue(EntityInstancingShaderPatch.patch(name, callback.getReturnValue(), getShaderSource(peer), body, modern, true, blocks, bindings)); }
            finally { arcadium$readingPeer.remove(); }
        } catch (IllegalStateException noContext) {}
    }
}
