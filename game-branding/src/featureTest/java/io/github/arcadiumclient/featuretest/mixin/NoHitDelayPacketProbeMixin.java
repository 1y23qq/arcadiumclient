package io.github.arcadiumclient.featuretest.mixin;

import io.github.arcadiumclient.featuretest.NoHitDelay034Qa;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class NoHitDelayPacketProbeMixin {
    @Inject(method = "sendPacket", at = @At("HEAD"))
    private void arcadium$recordNoHitDelayPacket(Packet<?> packet, CallbackInfo ci) {
        NoHitDelay034Qa.packet(packet);
    }
}
