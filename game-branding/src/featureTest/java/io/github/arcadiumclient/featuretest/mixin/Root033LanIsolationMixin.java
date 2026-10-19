package io.github.arcadiumclient.featuretest.mixin;

import io.github.arcadiumclient.featuretest.Root033Qa;
import java.io.IOException;
import java.net.MulticastSocket;
import net.minecraft.client.network.LanServerQueryManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LanServerQueryManager.LanServerDetector.class)
public abstract class Root033LanIsolationMixin {
    @Redirect(method = "<init>", at = @At(value = "NEW", target = "java/net/MulticastSocket"))
    private MulticastSocket arcadium$isolateLan(int port) throws IOException {
        if (Root033Qa.isolatingNetwork()) {
            Root033Qa.blockedLan();
            throw new IOException("LAN discovery disabled in the isolated viewport fixture");
        }
        return new MulticastSocket(port);
    }
}
