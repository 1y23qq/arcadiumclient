// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package org.polyfrost.redaction.mixin.client.legacy;

import net.minecraft.client.render.GameRenderer;
import org.polyfrost.redaction.client.RedactionConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin_CustomHandFOV {
    @ModifyArg(method = "renderItemInHand", at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/glu/Project;gluPerspective(FFFF)V"), index = 0)
    private float redaction$handFov(float original) {
        return RedactionConfig.INSTANCE.getCustomHandFOV() ? Math.max(1, Math.min(179, RedactionConfig.INSTANCE.getHandFOV())) : original;
    }
}
