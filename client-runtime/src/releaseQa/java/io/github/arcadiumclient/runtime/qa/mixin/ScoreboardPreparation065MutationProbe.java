// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.arcadiumclient.runtime.qa.ScoreboardPreparation065Qa;
import net.minecraft.client.gui.GameGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameGui.class)
public abstract class ScoreboardPreparation065MutationProbe {
    @WrapOperation(method = "renderScoreboardObjective", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GameGui;fill(IIIII)V", ordinal = 0))
    private void scope$midFrame(int x0, int y0, int x1, int y1, int color, Operation<Void> original) {
        ScoreboardPreparation065Qa.row(); original.call(x0, y0, x1, y1, color);
    }
}
