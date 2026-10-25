// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.mixin.gui;

import io.github.arcadiumclient.modern.gui.ModernScreens;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Gui.class)
public abstract class MixinModernScreens {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen arcadium$screen(Screen screen) { return ModernScreens.replace(screen); }

    @ModifyVariable(method = "setScreen", at = @At("STORE"), argsOnly = true)
    private Screen arcadium$resolvedScreen(Screen screen) { return ModernScreens.replace(screen); }
}
