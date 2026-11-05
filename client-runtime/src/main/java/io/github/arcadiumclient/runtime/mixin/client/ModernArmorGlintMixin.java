// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.client.optimizations.ModernArmorGlintPipeline;
import net.minecraft.client.render.entity.layer.AbstractArmorLayer;
import net.minecraft.client.render.model.Model;
import net.minecraft.entity.living.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AbstractArmorLayer.class)
public abstract class ModernArmorGlintMixin {
    @WrapMethod(method = "renderEnchantmentGlint")
    private void arcadium$modernGlint(LivingEntity entity, Model model, float walk, float speed, float delta, float age, float yaw, float pitch, float scale, Operation<Void> original) {
        int scope = ModernArmorGlintPipeline.beginGlint(this, entity, model);
        try { original.call(entity, model, walk, speed, delta, age, yaw, pitch, scale); } finally { ModernArmorGlintPipeline.endGlint(scope); }
    }
}
