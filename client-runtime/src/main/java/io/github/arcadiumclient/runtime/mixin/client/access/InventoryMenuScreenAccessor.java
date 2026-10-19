// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client.access;

import net.minecraft.client.gui.screen.game.inventory.InventoryMenuScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(InventoryMenuScreen.class)
public interface InventoryMenuScreenAccessor {
    @Accessor("x") int arcadium$left();
    @Accessor("y") int arcadium$top();
    @Accessor("backgroundWidth") int arcadium$width();
    @Accessor("backgroundHeight") int arcadium$height();
}
