// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.
package io.github.arcadiumclient.runtime.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.LoginS2CPacket;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class StaleDisconnectMixin {
    @Unique private volatile boolean arcadium$joined;

    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void arcadium$joinedWorld(LoginS2CPacket packet, CallbackInfo ci) { arcadium$joined = true; }

    @Inject(method = "onDisconnect", at = @At("HEAD"), cancellable = true)
    private void arcadium$ignoreOldSession(Text reason, CallbackInfo ci) {
        if (arcadium$joined && Minecraft.getInstance().getNetworkHandler() != (Object) this) ci.cancel();
    }
}
