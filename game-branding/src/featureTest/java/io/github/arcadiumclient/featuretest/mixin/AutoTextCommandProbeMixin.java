package io.github.arcadiumclient.featuretest.mixin;

import io.github.arcadiumclient.featuretest.Messaging033Qa;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.server.network.handler.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class AutoTextCommandProbeMixin {
    @Shadow @Final private MinecraftServer server;
    @Shadow public ServerPlayerEntity player;

    @Inject(method = "handleChatMessage", at = @At("HEAD"), cancellable = true)
    private void arcadium$recordAutoTextCommand(ChatMessageC2SPacket packet, CallbackInfo ci) {
        if ((Boolean.getBoolean("arcadium.release033Qa") || Boolean.getBoolean("arcadium.autoText034Qa"))
            && Messaging033Qa.interceptCommand(server, player, packet.getMessage())) ci.cancel();
    }
}
