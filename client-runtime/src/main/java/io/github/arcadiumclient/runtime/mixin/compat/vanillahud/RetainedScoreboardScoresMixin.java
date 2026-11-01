// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.vanillahud;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.ScoreboardPreparation;
import java.util.Collection;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardScore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@IfModLoaded("vanillahud")
@Mixin(Scoreboard.class)
public abstract class RetainedScoreboardScoresMixin {
    @Inject(method = "getScores(Lnet/minecraft/scoreboard/ScoreboardObjective;)Ljava/util/Collection;", at = @At("HEAD"), cancellable = true)
    private void arcadium$cachedScores(ScoreboardObjective objective, CallbackInfoReturnable<Collection<ScoreboardScore>> callback) {
        Collection<ScoreboardScore> scores = ScoreboardPreparation.scores((Scoreboard) (Object) this, objective);
        if (scores != null) callback.setReturnValue(scores);
    }

    @Inject(method = "getScores(Lnet/minecraft/scoreboard/ScoreboardObjective;)Ljava/util/Collection;", at = @At("RETURN"))
    private void arcadium$rememberScores(ScoreboardObjective objective, CallbackInfoReturnable<Collection<ScoreboardScore>> callback) {
        ScoreboardPreparation.observedScores((Scoreboard) (Object) this, objective, callback.getReturnValue());
    }
}
