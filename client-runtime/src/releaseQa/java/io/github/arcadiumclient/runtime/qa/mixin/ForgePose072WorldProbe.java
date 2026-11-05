// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.qa.ForgePose072Qa;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GameRenderer.class)
public abstract class ForgePose072WorldProbe {
    @WrapMethod(method = "renderWorld(FJ)V")
    private void forgePose$world(float delta, long deadline, Operation<Void> original) {
        if (!ForgePose072Qa.enabled()) { original.call(delta, deadline); return; }
        ForgePose072Qa.beforeWorld();
        try { original.call(.5f, deadline); } finally { ForgePose072Qa.afterWorld(); }
    }
}
