// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.GameGui;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.polyfrost.oneconfig.internal.emoji.EmojiText;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GameGui.class)
public class ScoreboardEmojiMixin {
    @WrapMethod(method = "func_96136_a(Lnet/minecraft/scoreboard/ScoreObjective;IILnet/minecraft/client/gui/FontRenderer;)V", remap = false)
    private void arcadium$scoreboard(ScoreboardObjective objective, int width, int height, TextRenderer font, Operation<Void> original) {
        boolean previous = EmojiText.beginScoreboard();
        try { original.call(objective, width, height, font); } finally { EmojiText.endScoreboard(previous); }
    }
}
