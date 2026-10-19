package io.github.arcadiumclient.featuretest.mixin;

import net.minecraft.client.gui.screen.inventory.menu.CreativeInventoryScreen;
import net.minecraft.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CreativeInventoryScreen.class)
public interface CreativeProbeAccess {
    @Accessor("selectedTab") static int arcadium$selectedTab() { throw new AssertionError(); }
    @Accessor("selectedTab") static void arcadium$selectedTab(int value) { throw new AssertionError(); }
    @Invoker("setSelectedTab") void arcadium$selectTab(CreativeModeTab tab);
    @Invoker("isInRow") boolean arcadium$isInRow(CreativeModeTab tab, int x, int y);
}
