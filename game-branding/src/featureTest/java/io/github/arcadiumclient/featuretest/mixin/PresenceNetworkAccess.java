package io.github.arcadiumclient.featuretest.mixin;

import java.util.Map;
import java.util.UUID;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientPlayNetworkHandler.class)
public interface PresenceNetworkAccess {
    @Accessor("onlinePlayers") Map<UUID, PlayerInfo> arcadium$onlinePlayers();
}
