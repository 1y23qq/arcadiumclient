// Copyright (c) 2026 0aurorq3.
// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.branding;

import io.github.arcadiumclient.capture.SdlCaptureCompatibility;
import io.github.arcadiumclient.window.GameWindowIcon;
import java.nio.ByteBuffer;
import org.lwjgl.system.Platform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "pl.tomgirl.pylon.window.DisplaySdl", remap = false)
public abstract class SdlCaptureMixin {
    @Shadow private long handle;

    @Redirect(method = "initSdlVideo", at = @At(value = "INVOKE", target = "Lorg/lwjgl/sdl/SDLInit;SDL_Init(I)Z"), require = 1)
    private boolean arcadium$captureWindowClass(int flags) {
        return SdlCaptureCompatibility.initVideo(flags);
    }

    @Inject(method = "create", at = @At(value = "INVOKE", target = "Lpl/tomgirl/pylon/window/MainThread;run(Ljava/lang/Runnable;)V", ordinal = 1), require = 1)
    private void arcadium$iconBeforeVisible(CallbackInfo callback) {
        GameWindowIcon.applySdl(handle);
    }

    @Inject(method = "setIcon", at = @At("HEAD"), cancellable = true)
    private void arcadium$keepGameIcon(ByteBuffer[] icons, CallbackInfoReturnable<Integer> callback) {
        if (Platform.get() != Platform.WINDOWS || GameWindowIcon.buffers() == null) return;
        if (handle <= 0 || GameWindowIcon.applySdl(handle)) callback.setReturnValue(1);
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void arcadium$detachGameIcon(CallbackInfo callback) {
        GameWindowIcon.releaseSdl(handle);
    }
}
