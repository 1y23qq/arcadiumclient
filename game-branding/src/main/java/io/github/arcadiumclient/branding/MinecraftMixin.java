// Copyright (c) 2026 0aurorq3.
// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.branding;

import io.github.arcadiumclient.capture.SdlCaptureCompatibility;
import io.github.arcadiumclient.window.GameWindowIcon;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
 private static final String TITLE = "Arcadium Client 1.8.9" + (SdlCaptureCompatibility.isEnabled() ? " (Minecraft)" : "");
 @Inject(method = "initDisplay", at = @At("TAIL"))
 private void arcadium$window(CallbackInfo callback) {
  Display.setTitle(TITLE);
  var buffers = GameWindowIcon.buffers();
  if (buffers != null) Display.setIcon(buffers);
 }
 @Inject(method = "tick", at = @At("TAIL"))
 private void arcadium$title(CallbackInfo callback) { if (!TITLE.equals(Display.getTitle())) Display.setTitle(TITLE); }
}
