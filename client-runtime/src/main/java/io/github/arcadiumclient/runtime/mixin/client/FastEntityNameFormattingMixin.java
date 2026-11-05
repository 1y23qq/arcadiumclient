// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import io.github.arcadiumclient.runtime.client.optimizations.EntityNameFormatting;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntityRenderer.class)
public abstract class FastEntityNameFormattingMixin {
    @WrapOperation(method = "applyRotation(Lnet/minecraft/entity/living/LivingEntity;FFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/text/Formatting;strip(Ljava/lang/String;)Ljava/lang/String;"))
    private String arcadium$formatRotationName(String text, Operation<String> original) {
        return BetaOptimizationsConfig.getCachedNameMasking() ? EntityNameFormatting.strip(text) : original.call(text);
    }
}
