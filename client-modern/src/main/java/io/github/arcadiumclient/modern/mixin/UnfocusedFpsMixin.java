// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.modern.client.features.UnfocusedFpsPolicy;
import net.minecraft.client.Minecraft;
import org.polyfrost.oneconfig.internal.OneConfigConfig;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(FramerateLimitTracker.class)
public abstract class UnfocusedFpsMixin {
    @WrapMethod(method = "getFramerateLimit")
    private int arcadium$cap(Operation<Integer> original) {
        int limit = original.call();
        Minecraft mc = Minecraft.getInstance();
        return UnfocusedFpsPolicy.cap(limit, OneConfigConfig.limitUnfocusedFps, OneConfigConfig.unfocusedFps, mc.isWindowActive());
    }
}
