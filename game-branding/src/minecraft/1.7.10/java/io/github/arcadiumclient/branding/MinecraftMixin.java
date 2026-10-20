// Copyright (c) 2026 0aurorq3.
// SPDX-License-Identifier: MIT
package io.github.arcadiumclient.branding;

import io.github.arcadiumclient.window.GameBranding;
import io.github.arcadiumclient.window.GameWindowIcon;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @ModifyArg(method = "init", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;setTitle(Ljava/lang/String;)V"), index = 0)
    private String arcadium$windowTitle(String original) {
        return GameBranding.windowTitle();
    }

    @ModifyArg(method = "init", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;setIcon([Ljava/nio/ByteBuffer;)I"), index = 0)
    private ByteBuffer[] arcadium$windowIcon(ByteBuffer[] original) {
        ByteBuffer[] icons = GameWindowIcon.buffers();
        return icons == null ? original : icons;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void arcadium$keepWindowTitle(CallbackInfo callback) {
        String title = GameBranding.windowTitle();
        if (!title.equals(Display.getTitle())) Display.setTitle(title);
    }
}
