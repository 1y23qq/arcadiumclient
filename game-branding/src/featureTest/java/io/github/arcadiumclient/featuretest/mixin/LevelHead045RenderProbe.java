// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.featuretest.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.featuretest.LevelHead045Qa;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "io.github.arcadiumclient.runtime.client.features.LevelHead", remap = false, priority = 400)
public abstract class LevelHead045RenderProbe {
    @WrapMethod(method = "render")
    private static void arcadium$levelHeadState(Entity entity, String name, double x, double y, double z, Operation<Void> original) {
        Object before = LevelHead045Qa.beforeRender(entity);
        try { original.call(entity, name, x, y, z); }
        finally { LevelHead045Qa.afterRender(before); }
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glBegin(I)V", remap = false))
    private static void arcadium$levelHeadBackground(Entity entity, String name, double x, double y, double z, CallbackInfo ci) {
        LevelHead045Qa.background();
    }
}
