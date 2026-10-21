// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client.branding;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.client.branding.NametagLayout;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.living.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntityRenderer.class)
public abstract class SneakingNametagBadgeMixin {
    @WrapMethod(method = "renderNameTag(Lnet/minecraft/entity/living/LivingEntity;DDD)V")
    private void arcadium$sneakingLayout(LivingEntity entity, double x, double y, double z, Operation<Void> original) {
        NametagLayout.Scope previous = NametagLayout.begin(entity, entity.getDisplayName().getFormattedString());
        try { original.call(entity, x, y, z); }
        finally { NametagLayout.end(previous); }
    }
}
