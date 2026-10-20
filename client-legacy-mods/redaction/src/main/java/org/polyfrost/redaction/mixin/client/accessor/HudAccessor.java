package org.polyfrost.redaction.mixin.client.accessor;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

//~ if <26.2 'Hud' -> 'Gui'
@Mixin(net.minecraft.client.gui.Gui.class)
public interface HudAccessor {
    @Invoker
    //~ if <26.1 'invokeExtractSlot' -> 'invokeRenderSlot'
    void invokeRenderSlot(GuiGraphics graphics, int x, int y, DeltaTracker deltaTracker, Player player, ItemStack itemStack, int seed);
}
