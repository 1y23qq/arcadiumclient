// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.
package io.github.arcadiumclient.runtime.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class ServerSwitchConnectionMixin {
    @Inject(method = "setWorld(Lnet/minecraft/client/world/ClientWorld;Ljava/lang/String;)V", at = @At("HEAD"))
    private void arcadium$leavePreviousServer(ClientWorld next, String message, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (next == null && client.world != null && client.getNetworkHandler() != null
            && client.getNetworkHandler().getConnection().isConnected()) client.world.disconnect();
    }
}
