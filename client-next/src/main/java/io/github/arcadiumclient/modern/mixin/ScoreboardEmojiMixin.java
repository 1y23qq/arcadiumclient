// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.scores.Objective;
import org.polyfrost.oneconfig.internal.emoji.EmojiText;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Hud.class)
public class ScoreboardEmojiMixin {
    @WrapMethod(method = "displayScoreboardSidebar")
    private void arcadium$scoreboard(GuiGraphicsExtractor graphics, Objective objective, Operation<Void> original) {
        boolean previous = EmojiText.beginScoreboard();
        try { original.call(graphics, objective); } finally { EmojiText.endScoreboard(previous); }
    }
}
