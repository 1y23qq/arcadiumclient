// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import io.github.arcadiumclient.runtime.client.features.UnfocusedFrameLimit;
import org.lwjgl.opengl.Display;
import org.polyfrost.oneconfig.internal.OneConfigConfig;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = Minecraft.class, priority = 500)
public abstract class MixinUnfocusedFps {
    @WrapMethod(method = "getMaxFramerate")
    private int arcadium$unfocusedFps(Operation<Integer> original) {
        if (!OneConfigConfig.limitUnfocusedFps || !Display.isCreated() || Display.isActive()) return original.call();
        Minecraft client = Minecraft.getInstance();
        int activeLimit = client.options == null ? original.call() : client.options.fpsLimit;
        return UnfocusedFrameLimit.apply(activeLimit, false, OneConfigConfig.unfocusedFps);
    }
}
