// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.mixin.gui;

import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.world.level.WorldDataConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CreateWorldScreen.class)
public interface CreateWorldAccess {
    @Invoker("onCreate") void arcadium$create();
    @Invoker("openDataPackSelectionScreen") void arcadium$dataPacks(WorldDataConfiguration configuration);
    @Invoker("openExperimentsScreen") void arcadium$experiments(WorldDataConfiguration configuration);
}
