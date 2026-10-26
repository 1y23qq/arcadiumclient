// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import io.github.arcadiumclient.runtime.client.features.BackgroundFramePacer;
import io.github.arcadiumclient.runtime.client.features.UnfocusedFrameLimit;
import java.util.concurrent.locks.LockSupport;
import org.lwjgl.opengl.Display;
import org.polyfrost.oneconfig.internal.OneConfigConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Minecraft.class, priority = 500)
public abstract class MixinUnfocusedFps {
    @Unique private final BackgroundFramePacer arcadium$backgroundPacer = new BackgroundFramePacer();

    @Unique private static boolean arcadium$backgroundLimit() {
        return OneConfigConfig.limitUnfocusedFps && Display.isCreated() && !Display.isActive();
    }

    @WrapMethod(method = "getMaxFramerate")
    private int arcadium$unfocusedFps(Operation<Integer> original) {
        if (!arcadium$backgroundLimit()) return original.call();
        Minecraft client = Minecraft.getInstance();
        int activeLimit = client.options == null ? original.call() : client.options.fpsLimit;
        return UnfocusedFrameLimit.apply(activeLimit, false, OneConfigConfig.unfocusedFps);
    }

    @WrapOperation(method = "runGame", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;sync(I)V"))
    private void arcadium$singleBackgroundWait(int fps, Operation<Void> original) {
        if (!arcadium$backgroundLimit()) original.call(fps);
    }

    @Inject(method = "runGame", at = @At("RETURN"))
    private void arcadium$backgroundFrame(CallbackInfo ci) {
        if (!arcadium$backgroundLimit()) { arcadium$backgroundPacer.reset(); return; }
        Minecraft client = Minecraft.getInstance();
        int normal = client.options == null ? 260 : client.options.fpsLimit;
        int cap = UnfocusedFrameLimit.apply(normal, false, OneConfigConfig.unfocusedFps);
        long remaining = arcadium$backgroundPacer.remaining(System.nanoTime(), cap);
        for (int waits = 0; remaining > 0 && waits < 100; waits++) {
            if (!arcadium$backgroundLimit() || Display.isCloseRequested() || Thread.currentThread().isInterrupted()) break;
            LockSupport.parkNanos(Math.min(remaining, 2_000_000L));
            remaining = arcadium$backgroundPacer.remaining(System.nanoTime(), cap);
        }
        arcadium$backgroundPacer.mark(System.nanoTime());
    }
}
