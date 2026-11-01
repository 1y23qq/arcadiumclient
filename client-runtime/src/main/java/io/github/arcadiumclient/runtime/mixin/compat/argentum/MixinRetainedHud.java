// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.RetainedHudBuffers;
import io.github.arcadiumclient.runtime.client.optimizations.GpuNameTags;
import io.github.arcadiumclient.runtime.client.optimizations.GpuPlayerPipeline;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.VertexBuffer;
import net.minecraft.client.render.vertex.VertexFormat;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@IfModLoaded("argentum")
@Mixin(targets = "dev.rdh.argentum.impl.render.gui.TextBatcher", remap = false)
public abstract class MixinRetainedHud {
    @Invoker("draw") protected abstract void arcadium$draw(VertexBuffer buffer, VertexFormat format, float x, float y);
    @Unique private boolean arcadium$hud;
    @Unique private final RetainedHudBuffers arcadium$retained = new RetainedHudBuffers();
    @Unique private final RetainedHudBuffers.Drawer arcadium$drawer = (buffer, format) -> arcadium$draw(buffer, format, 0, 0);

    @WrapMethod(method = "flushElementBatch")
    private void arcadium$hudScope(TextureManager textures, Operation<Void> original) {
        boolean previous = arcadium$hud; arcadium$hud = true;
        try { original.call(textures); } finally { arcadium$hud = previous; }
    }

    @Inject(method = "upload", at = @At("HEAD"), cancellable = true, remap = false)
    private void arcadium$retain(BufferBuilder buffer, CallbackInfo callback) {
        if (arcadium$hud && arcadium$retained.render(buffer, arcadium$drawer)) callback.cancel();
    }

    @Inject(method = "clearCaches", at = @At("HEAD"), remap = false)
    private void arcadium$invalidate(CallbackInfo callback) { arcadium$retained.close(); GpuNameTags.invalidateAll(); GpuPlayerPipeline.reset(); }
}
