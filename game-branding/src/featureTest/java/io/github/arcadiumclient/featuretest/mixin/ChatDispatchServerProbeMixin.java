package io.github.arcadiumclient.featuretest.mixin;

import io.github.arcadiumclient.featuretest.ChatDispatch035Qa;
import net.minecraft.network.packet.c2s.play.ArmSwingC2SPacket;
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
public abstract class ChatDispatchServerProbeMixin {
    @Shadow @Final private MinecraftServer server;
    @Shadow public ServerPlayerEntity player;

    @Inject(method = "handleChatMessage", at = @At("HEAD"), cancellable = true)
    private void arcadium$chatDispatchReceived(ChatMessageC2SPacket packet, CallbackInfo ci) {
        if (Boolean.getBoolean("arcadium.chatDispatch035Qa") && ChatDispatch035Qa.received(server, player, packet.getMessage())) ci.cancel();
    }

    @Inject(method = "handleArmSwing", at = @At("HEAD"), cancellable = true)
    private void arcadium$chatDispatchUnrelated(ArmSwingC2SPacket packet, CallbackInfo ci) {
        if (Boolean.getBoolean("arcadium.chatDispatch035Qa") && ChatDispatch035Qa.unrelated(server, player)) ci.cancel();
    }
}
