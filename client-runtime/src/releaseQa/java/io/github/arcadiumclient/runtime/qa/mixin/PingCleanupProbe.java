package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.SessionUiQa;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.MultiplayerServerListPinger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiplayerServerListPinger.class)
public abstract class PingCleanupProbe {
    @Inject(method = "cancel", at = @At("HEAD"))
    private void release$cancelThread(CallbackInfo ci) {
        if (SessionUiQa.enabled() && Minecraft.getInstance().isOnSameThread()) SessionUiQa.clientPingCleanup++;
    }
}
