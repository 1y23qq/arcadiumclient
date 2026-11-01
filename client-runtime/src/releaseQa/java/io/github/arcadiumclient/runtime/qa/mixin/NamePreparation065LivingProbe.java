// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.client.branding.NametagLayout;
import io.github.arcadiumclient.runtime.qa.NamePreparation065Qa;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.living.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = LivingEntityRenderer.class, priority = 2300)
public abstract class NamePreparation065LivingProbe {
    @WrapMethod(method = "renderNameTag(Lnet/minecraft/entity/living/LivingEntity;DDD)V")
    private void names$compare(LivingEntity entity, double x, double y, double z, Operation<Void> original) {
        if (!NamePreparation065Qa.beginPair(entity)) { original.call(entity, x, y, z); return; }
        boolean normal = !entity.isSneaking();
        NametagLayout.Scope previous = normal ? NametagLayout.begin(entity, entity.getDisplayName().getFormattedString()) : null;
        try { original.call(entity, x, y, z); }
        finally { if (normal) NametagLayout.end(previous); }
        NamePreparation065Qa.actual();
        try { original.call(entity, x, y, z); }
        finally { NamePreparation065Qa.endPair(); }
    }
}
