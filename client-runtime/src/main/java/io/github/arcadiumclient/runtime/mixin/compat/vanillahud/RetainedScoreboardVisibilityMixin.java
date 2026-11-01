// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.vanillahud;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.ScoreboardPreparation;
import java.util.Collection;
import net.minecraft.scoreboard.ScoreboardScore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@IfModLoaded("vanillahud")
@Mixin(targets = "org.polyfrost.vanillahud.hud.ScoreboardHud", remap = false)
public abstract class RetainedScoreboardVisibilityMixin {
    @Inject(method = "showScorePoints", at = @At("HEAD"), cancellable = true)
    private void arcadium$cachedVisibility(Collection<ScoreboardScore> scores, CallbackInfoReturnable<Boolean> callback) {
        if (!ScoreboardPreparation.active()) return;
        Boolean decision = ScoreboardPreparation.visibility(this, scores);
        if (decision != null) callback.setReturnValue(decision);
    }

    @Inject(method = "showScorePoints", at = @At("RETURN"))
    private void arcadium$rememberVisibility(Collection<ScoreboardScore> scores, CallbackInfoReturnable<Boolean> callback) {
        if (ScoreboardPreparation.active()) ScoreboardPreparation.observedVisibility(this, scores, callback.getReturnValue());
    }
}
