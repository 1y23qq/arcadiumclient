// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.HologramNameFont073Evidence;
import net.minecraft.client.render.vertex.BufferBuilder;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets = "dev.rdh.argentum.impl.render.gui.TextBatcher", remap = false)
public abstract class HologramName073TextBatcherProbe {
    @Inject(method = "begin", at = @At("HEAD"), remap = false)
    private void hologram073$label(String text, boolean shadow, int style, float x, float y, @Coerce Object textures, CallbackInfoReturnable<Float> callback) { HologramNameFont073Evidence.label(this, text); }
    @Inject(method = "drawCached", at = @At("HEAD"), remap = false)
    private void hologram073$cachedBegin(@Coerce Object segment, int alpha, float x, float y, CallbackInfo callback) { HologramNameFont073Evidence.begin(this, segment, alpha, x, y); }
    @Inject(method = "drawCached", at = @At("RETURN"), remap = false)
    private void hologram073$cachedEnd(@Coerce Object segment, int alpha, float x, float y, CallbackInfo callback) { HologramNameFont073Evidence.end(this, segment); }
    @Inject(method = "upload", at = @At("HEAD"), remap = false)
    private void hologram073$cpu(BufferBuilder builder, CallbackInfo callback) { HologramNameFont073Evidence.cpu(this, builder); }
    @Inject(method = "draw", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glEnableClientState(I)V", ordinal = 0, remap = false), remap = false)
    private void hologram073$vbo(CallbackInfo callback) { HologramNameFont073Evidence.vbo(this); }
}
