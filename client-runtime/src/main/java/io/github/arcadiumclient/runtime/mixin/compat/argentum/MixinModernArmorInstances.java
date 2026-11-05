// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.ModernArmorGlintPipeline;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@IfModLoaded("argentum")
@Mixin(targets = "dev.rdh.argentum.impl.render.entity.instancing.Instances", remap = false)
public abstract class MixinModernArmorInstances {
    @Unique private static MethodHandle arcadium$currentCapture;
    @ModifyVariable(method = "add", at = @At("HEAD"), argsOnly = true, ordinal = 2, remap = false)
    private float arcadium$modernMatrix(float original) throws Throwable {
        if (!ModernArmorGlintPipeline.active()) return original;
        if (arcadium$currentCapture == null) {
            Class<?> type = Class.forName("dev.rdh.argentum.impl.render.entity.instancing.EntityCapture");
            arcadium$currentCapture = MethodHandles.privateLookupIn(type, MethodHandles.lookup()).unreflect(type.getDeclaredMethod("current"));
        }
        return ModernArmorGlintPipeline.effectTime(arcadium$currentCapture.invoke(), original);
    }
}
