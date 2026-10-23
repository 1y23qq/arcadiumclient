// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client.branding;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.client.branding.NametagLayout;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EntityRenderer.class)
public abstract class EntityNametagBadgeMixin {
    @WrapMethod(method = "renderNameTag(Lnet/minecraft/entity/Entity;Ljava/lang/String;DDDI)V")
    private void arcadium$nametagLayout(Entity entity, String name, double x, double y, double z, int distance,
                                        Operation<Void> original) {
        NametagLayout.Scope previous = NametagLayout.begin(entity, name);
        try {
            original.call(entity, NametagLayout.display(name), x, y, z, distance);
            io.github.arcadiumclient.runtime.client.features.LevelHead.render(entity, name, x, y, z);
        }
        finally { NametagLayout.end(previous); }
    }
}
