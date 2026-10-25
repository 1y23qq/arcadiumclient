// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.arcadiumclient.modern.client.features.LightLevelOverlay;
import io.github.arcadiumclient.modern.client.features.KillEffects;
import io.github.arcadiumclient.modern.client.hypixel.ModernHypixel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LightLevelMixin {
    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void arcadium$light(PoseStack pose, LevelRenderState state, SubmitNodeCollector collector, CallbackInfo ci) {
        LightLevelOverlay.submit(pose, state, collector);
        KillEffects.submit(pose, state, collector);
        ModernHypixel.submit(pose, state, collector);
    }
}
