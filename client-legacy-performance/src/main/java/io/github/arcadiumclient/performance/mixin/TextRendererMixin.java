// Copyright (c) 2026 0aurorq3.
// SPDX-License-Identifier: MIT
package io.github.arcadiumclient.performance.mixin;

import io.github.arcadiumclient.performance.GlyphBatch;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.client.resource.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextRenderer.class)
public abstract class TextRendererMixin {
    @Shadow private float r;
    @Shadow private float g;
    @Shadow private float b;
    @Shadow private float a;
    @Unique private final GlyphBatch arcadium$glyphBatch = new GlyphBatch();
    @Unique private String arcadium$lowercaseText;

    @Inject(method = "drawLayer(Ljava/lang/String;Z)V", at = @At("HEAD"))
    private void arcadium$beginFontBatch(String text, boolean shadow, CallbackInfo callback) {
        arcadium$lowercaseText = null;
        arcadium$glyphBatch.begin(r, g, b, a);
    }

    @Inject(method = "drawLayer(Ljava/lang/String;Z)V", at = @At("RETURN"))
    private void arcadium$finishFontBatch(String text, boolean shadow, CallbackInfo callback) {
        arcadium$glyphBatch.finish();
        arcadium$lowercaseText = null;
    }

    @Redirect(method = "drawLayer(Ljava/lang/String;Z)V", at = @At(value = "INVOKE", target = "Ljava/lang/String;toLowerCase()Ljava/lang/String;"))
    private String arcadium$reuseFormattingText(String text) {
        if (arcadium$lowercaseText == null) arcadium$lowercaseText = text.toLowerCase();
        return arcadium$lowercaseText;
    }

    @Redirect(method = {"drawBasicGlyph", "bindFontPageTexture"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/TextureManager;bind(Lnet/minecraft/client/resource/Identifier;)V"))
    private void arcadium$bindGlyphTexture(TextureManager manager, Identifier texture) {
        arcadium$glyphBatch.bind(manager, texture);
    }

    @Redirect(method = "drawLayer(Ljava/lang/String;Z)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glColor4f(FFFF)V"))
    private void arcadium$glyphColor(float red, float green, float blue, float alpha) {
        arcadium$glyphBatch.color(red, green, blue, alpha);
    }

    @Redirect(method = "drawLayer(Ljava/lang/String;Z)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V"))
    private void arcadium$flushBeforeDecoration(int capability) {
        arcadium$glyphBatch.disable(capability);
    }

    @Redirect(method = {"drawBasicGlyph", "drawUnicodeGlyph"}, at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glBegin(I)V"))
    private void arcadium$beginGlyph(int mode) {
        arcadium$glyphBatch.beginGlyph(mode);
    }

    @Redirect(method = {"drawBasicGlyph", "drawUnicodeGlyph"}, at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTexCoord2f(FF)V"))
    private void arcadium$glyphTexture(float u, float v) {
        arcadium$glyphBatch.texture(u, v);
    }

    @Redirect(method = {"drawBasicGlyph", "drawUnicodeGlyph"}, at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glVertex3f(FFF)V"))
    private void arcadium$glyphVertex(float x, float y, float z) {
        arcadium$glyphBatch.vertex(x, y, z);
    }

    @Redirect(method = {"drawBasicGlyph", "drawUnicodeGlyph"}, at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glEnd()V"))
    private void arcadium$endGlyph() {
        arcadium$glyphBatch.endGlyph();
    }
}
