package io.github.arcadiumclient.featuretest.mixin;
import io.github.arcadiumclient.featuretest.FeatureQa;

import net.minecraft.client.gui.screen.inventory.menu.InventoryMenuScreen;
import net.minecraft.inventory.slot.InventorySlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(InventoryMenuScreen.class)
public interface ContainerProbeAccess {
    @Accessor("x") int arcadium$left();
    @Accessor("y") int arcadium$top();
    @Accessor("backgroundWidth") int arcadium$backgroundWidth();
    @Accessor("backgroundHeight") int arcadium$backgroundHeight();
    @Accessor("hoveredSlot") InventorySlot arcadium$hovered();
    @Invoker("getSlot") InventorySlot arcadium$slotAt(int x, int y);
}
