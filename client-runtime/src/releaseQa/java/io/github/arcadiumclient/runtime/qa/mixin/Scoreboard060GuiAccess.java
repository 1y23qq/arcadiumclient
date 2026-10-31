package io.github.arcadiumclient.runtime.qa.mixin;

import net.minecraft.client.gui.GameGui;
import net.minecraft.client.render.Window;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameGui.class)
public interface Scoreboard060GuiAccess {
    @Invoker("renderScoreboardObjective") void release$scoreboard(ScoreboardObjective objective, Window window);
}
