// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.polyblur;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.EfficientMotionBlur;
import kotlin.ranges.IntRange;
import net.minecraft.client.render.PostChain;
import net.minecraft.client.render.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@IfModLoaded("polyblur")
@Mixin(targets = "org.polyfrost.polyblur.client.blur.phosphor.PhosphorBlurKt", remap = false)
public abstract class MixinHybridHistoryPass {
    @WrapWithCondition(method = "processLegacy", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/render/PostPass;process(F)V", remap = true), remap = false)
    private static boolean arcadium$historyPass(PostPass pass, float delta, PostChain chain, IntRange range) {
        return EfficientMotionBlur.process(pass, delta, chain, range);
    }
}
