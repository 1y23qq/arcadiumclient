// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.network.packet.Packet;
import io.github.arcadiumclient.runtime.client.features.CuteVisuals;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class MixinCuteVisualsPacket {
    @Inject(method = "sendPacket", at = @At("HEAD"))
    private void arcadium$cuteVisualsPacket(Packet packet, CallbackInfo ci) {
        CuteVisuals.packetSent(packet);
    }
}
