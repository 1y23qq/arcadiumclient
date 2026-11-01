// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.ScoreboardPreparation065Qa;
import java.util.Collection;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardScore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Scoreboard.class)
public abstract class ScoreboardPreparation065SortProbe {
    @Inject(method = "getScores(Lnet/minecraft/scoreboard/ScoreboardObjective;)Ljava/util/Collection;",
            at = @At(value = "INVOKE", target = "Ljava/util/Collections;sort(Ljava/util/List;Ljava/util/Comparator;)V", remap = false))
    private void scope$nativeSort(ScoreboardObjective objective, CallbackInfoReturnable<Collection<ScoreboardScore>> callback) { ScoreboardPreparation065Qa.noteSort(); }
}
