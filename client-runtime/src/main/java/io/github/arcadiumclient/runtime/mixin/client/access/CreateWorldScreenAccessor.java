// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client.access;

import net.minecraft.client.gui.screen.menu.world.CreateWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CreateWorldScreen.class)
public interface CreateWorldScreenAccessor {
    @Accessor("displayNameField") TextFieldWidget arcadium$nameField();
    @Accessor("seedField") TextFieldWidget arcadium$seedField();
    @Accessor("moreOptionsOpen") boolean arcadium$moreOptions();
    @Accessor("worldSaveName") String arcadium$saveName();
    @Accessor("worldName") void arcadium$worldName(String value);
    @Accessor("seed") void arcadium$seed(String value);
    @Accessor("seed") String arcadium$currentSeed();
    @Accessor("firstGameModeDescriptionLine") String arcadium$modeDescription();
    @Accessor("secondGameModeDescriptionLine") String arcadium$modeDescriptionExtra();
    @Invoker("getSaveDirectoryName") void arcadium$updateSaveName();
    @Invoker("buttonClicked") void arcadium$click(ButtonWidget button);
}
