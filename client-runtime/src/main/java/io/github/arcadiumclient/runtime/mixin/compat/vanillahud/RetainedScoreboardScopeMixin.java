// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.vanillahud;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import io.github.arcadiumclient.runtime.client.optimizations.ScoreboardPreparation;
import net.minecraft.client.gui.GameGui;
import net.minecraft.client.render.Window;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;

@IfModLoaded("vanillahud")
@Mixin(value = GameGui.class, priority = 2500)
public abstract class RetainedScoreboardScopeMixin {
    @WrapMethod(method = "renderScoreboardObjective")
    private void arcadium$scoreboardPreparation(ScoreboardObjective objective, Window window, Operation<Void> original) {
        ScoreboardPreparation.Scope previous = ScoreboardPreparation.begin(objective, BetaOptimizationsConfig.getRetainedScoreboardPreparation());
        try { original.call(objective, window); }
        finally { ScoreboardPreparation.end(previous); }
    }
}
