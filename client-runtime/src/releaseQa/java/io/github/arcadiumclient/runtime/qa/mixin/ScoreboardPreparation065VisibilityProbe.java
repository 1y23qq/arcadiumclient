// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.ScoreboardPreparation065Qa;
import java.util.Collection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "org.polyfrost.vanillahud.hud.ScoreboardHud", remap = false)
public abstract class ScoreboardPreparation065VisibilityProbe {
    @Inject(method = "areScoresRepeating", at = @At("HEAD"))
    private void scope$repeating(Collection<?> scores, CallbackInfoReturnable<Boolean> callback) { ScoreboardPreparation065Qa.noteRepeating(); }
    @Inject(method = "areScoresConsecutive", at = @At("HEAD"))
    private void scope$consecutive(Collection<?> scores, CallbackInfoReturnable<Boolean> callback) { ScoreboardPreparation065Qa.noteConsecutive(); }
}
