// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.GameGui;
import net.minecraft.client.render.Window;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.polyfrost.oneconfig.internal.emoji.EmojiText;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GameGui.class)
public class ScoreboardEmojiMixin {
    @WrapMethod(method = "renderScoreboardObjective")
    private void arcadium$scoreboard(ScoreboardObjective objective, Window window, Operation<Void> original) {
        boolean previous = EmojiText.beginScoreboard();
        try { original.call(objective, window); } finally { EmojiText.endScoreboard(previous); }
    }
}
