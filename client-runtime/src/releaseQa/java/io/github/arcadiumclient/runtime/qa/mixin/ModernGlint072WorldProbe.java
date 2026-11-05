// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.qa.ModernGlint072Qa;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GameRenderer.class)
public abstract class ModernGlint072WorldProbe {
    @WrapMethod(method = "renderWorld(FJ)V")
    private void modern$world(float delta, long deadline, Operation<Void> original) {
        if (ModernGlint072Qa.enabled()) ModernGlint072Qa.world(delta, deadline, original); else original.call(delta, deadline);
    }
}
