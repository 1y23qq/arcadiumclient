// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.
package io.github.arcadiumclient.featuretest.mixin;

import io.github.arcadiumclient.featuretest.LegacyOverlay040Qa;
import net.minecraft.client.render.entity.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.entity.living.player.PlayerEntity;

@Mixin(PlayerRenderer.class)
public class LegacyOverlayHandProbe {
    @Inject(method="renderRightArm",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/model/ModelPart;render(F)V"))
    private void arcadium$handAlpha(PlayerEntity player,CallbackInfo ci){LegacyOverlay040Qa.handModel();}
}
