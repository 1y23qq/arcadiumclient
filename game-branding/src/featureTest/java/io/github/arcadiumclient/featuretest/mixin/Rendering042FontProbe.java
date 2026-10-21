// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.
package io.github.arcadiumclient.featuretest.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.featuretest.Rendering042Qa;
import net.minecraft.client.render.TextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = TextRenderer.class, priority = 400)
public abstract class Rendering042FontProbe {
    @Shadow private float x;
    @Shadow private float y;
    @WrapMethod(method = "drawLayer(Ljava/lang/String;Z)V")
    private void arcadium$recordText(String text, boolean shadow, Operation<Void> original) {
        float start = x, top = y;
        original.call(text, shadow);
        Rendering042Qa.draw(text, start, top, x, shadow);
    }
}
