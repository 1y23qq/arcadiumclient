// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.polyblur;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.EfficientMotionBlur;
import net.minecraft.client.render.PostChain;
import net.minecraft.client.render.pipeline.RenderTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@IfModLoaded("polyblur")
@Mixin(targets = "org.polyfrost.polyblur.client.blur.phosphor.HybridHandPhosphor", remap = false)
public abstract class MixinHybridHandHistory {
    @Shadow private static PostChain postChain;

    @WrapMethod(method = "render")
    private static void arcadium$history(RenderTarget target, Operation<Void> original) {
        boolean scoped = EfficientMotionBlur.begin(postChain, target);
        boolean completed = false;
        try {
            original.call(target);
            completed = true;
        } finally {
            if (scoped) EfficientMotionBlur.end(completed);
        }
    }
}
