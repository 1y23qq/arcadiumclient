// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client.optimizations;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import io.github.arcadiumclient.runtime.client.optimizations.FastInvisibleHolograms;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderDispatcher.class)
public abstract class FastInvisibleHologramMixin {
    @WrapWithCondition(method = "render(Lnet/minecraft/entity/Entity;DDDFFZ)Z", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/EntityRenderer;render(Lnet/minecraft/entity/Entity;DDDFF)V"))
    private boolean arcadium$nameOnlyHologram(EntityRenderer<?> renderer, Entity entity, double x, double y, double z, float yaw, float delta) {
        return !FastInvisibleHolograms.render(renderer, entity, x, y, z, yaw, delta);
    }
}
