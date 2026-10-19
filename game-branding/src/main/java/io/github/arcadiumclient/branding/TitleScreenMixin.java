// Copyright (c) 2026 0aurorq3.
// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.branding;

import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {
 @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/TitleScreen;drawString(Lnet/minecraft/client/render/TextRenderer;Ljava/lang/String;III)V", ordinal = 0), index = 1)
 private String arcadium$brand(String original) { return "Arcadium Client"; }
}
