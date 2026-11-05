// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.ModernArmorGlintPipeline;
import net.minecraft.entity.living.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@IfModLoaded("argentum")
@Mixin(targets = "dev.rdh.argentum.impl.render.entity.instancing.EntityCapture", priority = 1500, remap = false)
public abstract class MixinModernArmorGate {
    @TargetHandler(mixin = "org.polyfrost.overflowanimations.mixins.legacy.compat.MixinEntityCapture_Argentum", name = "overflowanimations$modernArmorGlint")
    @ModifyExpressionValue(method = "@MixinSquared:Handler", at = @At(value = "INVOKE",
            target = "Lorg/polyfrost/overflowanimations/legacy/tint/LegacyGlint;replacesArmorGlint()Z"), remap = false, require = 0)
    private boolean arcadium$modernCapture(boolean original, Object layer, LivingEntity entity, CallbackInfoReturnable<Boolean> callback) {
        return original && !ModernArmorGlintPipeline.canCapture(layer, entity);
    }
}
