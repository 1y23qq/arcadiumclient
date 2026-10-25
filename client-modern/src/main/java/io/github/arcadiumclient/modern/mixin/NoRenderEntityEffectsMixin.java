// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import io.github.arcadiumclient.modern.client.features.NoRender;
import io.github.arcadiumclient.modern.client.features.NoRenderPolicy.Effect;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class NoRenderEntityEffectsMixin {
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void arcadium$effects(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
        if (NoRender.effect(Effect.ENTITY_FIRE)) state.displayFireAnimation = false;
        if (NoRender.effect(Effect.ENTITY_SHADOWS)) { state.shadowRadius = 0; state.shadowPieces.clear(); }
    }
    @Inject(method = "finalizeRenderState", at = @At("RETURN"))
    private void arcadium$shadow(Entity entity, EntityRenderState state, CallbackInfo ci) {
        if (NoRender.effect(Effect.ENTITY_SHADOWS)) { state.shadowRadius = 0; state.shadowPieces.clear(); }
    }
}
