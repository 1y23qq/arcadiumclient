// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.menu.resourcepacks.ResourcePacksScreen;
import io.github.arcadiumclient.runtime.client.gui.ArcadiumResourcePacksScreen;
import io.github.arcadiumclient.runtime.mixin.client.access.ResourcePacksScreenAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Minecraft.class)
public abstract class MixinResourcePacksScreen {
    @ModifyVariable(method = "openScreen", at = @At("HEAD"), argsOnly = true)
    private Screen arcadium$resourcePacksScreen(Screen screen) {
        if (screen instanceof ResourcePacksScreen) {
            return new ArcadiumResourcePacksScreen(((ResourcePacksScreenAccessor) screen).arcadium$parent());
        }
        return screen;
    }
}
