// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client.access;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.living.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.util.List;

@Mixin(LivingEntityRenderer.class)
public interface LivingPipelineAccess {
    @Accessor("renderMarker") boolean arcadium$outline();
    @Accessor("layers") List<?> arcadium$layers();
    @Invoker("renderLayers") void arcadium$renderLayers(LivingEntity player,float walk,float speed,float delta,float age,float yaw,float pitch,float scale);
}
