// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.qa.ForgePose072Qa;
import net.minecraft.client.render.entity.layer.AbstractArmorLayer;
import net.minecraft.client.render.model.Model;
import net.minecraft.entity.living.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AbstractArmorLayer.class)
public abstract class ForgePose072GlintProbe {
    @WrapMethod(method = "renderEnchantmentGlint")
    private void forgePose$glint(LivingEntity entity, Model model, float walk, float speed, float delta, float bob, float yaw, float pitch, float scale, Operation<Void> original) {
        if (!ForgePose072Qa.enabled()) { original.call(entity, model, walk, speed, delta, bob, yaw, pitch, scale); return; }
        ForgePose072Qa.beginGlint();
        try { original.call(entity, model, walk, speed, delta, bob, yaw, pitch, scale); } finally { ForgePose072Qa.endGlint(); }
    }
}
