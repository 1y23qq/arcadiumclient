// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client.access;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.menu.resourcepacks.ResourcePacksScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ResourcePacksScreen.class)
public interface ResourcePacksScreenAccessor {
    @Accessor("parent") Screen arcadium$parent();
}
