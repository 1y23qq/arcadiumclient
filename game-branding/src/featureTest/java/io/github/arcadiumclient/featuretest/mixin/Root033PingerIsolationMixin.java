package io.github.arcadiumclient.featuretest.mixin;

import io.github.arcadiumclient.featuretest.Root033Qa;
import net.minecraft.client.network.MultiplayerServerListPinger;
import net.minecraft.client.options.ServerListEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiplayerServerListPinger.class)
public abstract class Root033PingerIsolationMixin {
    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void arcadium$isolatePing(ServerListEntry server, CallbackInfo callback) {
        if (!Root033Qa.isolatingNetwork()) return;
        Root033Qa.blockedPing();
        server.ping = -1;
        server.motd = "Offline QA fixture";
        callback.cancel();
    }
}
