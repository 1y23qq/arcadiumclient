// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client;

import io.github.arcadiumclient.runtime.client.optimizations.FastFramePresentation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.pipeline.RenderTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class FastFramePresentScopeMixin {
    @Shadow private RenderTarget renderTarget;
    @Shadow public int width;
    @Shadow public int height;

    @Inject(method = "runGame()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/pipeline/RenderTarget;draw(II)V"))
    private void arcadium$beginFinalPresent(CallbackInfo callback) { FastFramePresentation.begin(renderTarget, width, height); }

    @Inject(method = "runGame()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/pipeline/RenderTarget;draw(II)V", shift = At.Shift.AFTER))
    private void arcadium$endFinalPresent(CallbackInfo callback) { FastFramePresentation.end(); }

    @Inject(method = {"shutdown()V", "reloadResources()V"}, at = @At("HEAD"))
    private void arcadium$releasePresentation(CallbackInfo callback) { FastFramePresentation.release(); }
}
