// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.menu.world.SelectWorldScreen;
import net.minecraft.client.gui.screen.menu.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.menu.multiplayer.MultiplayerScreen;
import io.github.arcadiumclient.runtime.client.gui.ArcadiumServersScreen;
import io.github.arcadiumclient.runtime.client.gui.ArcadiumWorldsScreen;
import io.github.arcadiumclient.runtime.client.gui.ArcadiumCreateWorldScreen;
import io.github.arcadiumclient.runtime.client.gui.ArcadiumVideoSettingsScreen;
import io.github.arcadiumclient.runtime.mixin.client.access.MultiplayerScreenAccessor;
import io.github.arcadiumclient.runtime.mixin.client.access.SelectWorldScreenAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Minecraft.class)
public abstract class MixinArcadiumPlayScreens {
    @ModifyVariable(method = "openScreen", at = @At("HEAD"), argsOnly = true)
    private Screen arcadium$playScreen(Screen screen) {
        Screen video = ArcadiumVideoSettingsScreen.replace(screen);
        if (video != screen) return video;
        if (screen instanceof CreateWorldScreen create) {
            return new ArcadiumCreateWorldScreen(create);
        }
        if (screen instanceof SelectWorldScreen) {
            return new ArcadiumWorldsScreen(((SelectWorldScreenAccessor) screen).arcadium$parent());
        }
        if (screen instanceof MultiplayerScreen) {
            return new ArcadiumServersScreen(((MultiplayerScreenAccessor) screen).arcadium$parent());
        }
        return screen;
    }
}
