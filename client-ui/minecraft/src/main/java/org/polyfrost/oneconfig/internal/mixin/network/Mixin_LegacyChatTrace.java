package org.polyfrost.oneconfig.internal.mixin.network;

//? if <= 1.8.9 {
/*import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
//? if = 1.8.9
import net.minecraft.network.PacketFlow;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import org.polyfrost.oneconfig.internal.network.ChatSendTrace;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class Mixin_LegacyChatTrace {
    @Shadow private Channel channel;
    //? if = 1.7.10 {
    @Shadow @Final private boolean isClient;
    //?} else {
    @Shadow @Final private PacketFlow flow;
    //?}

    @Unique private boolean arcadium$isClient() {
        //? if = 1.7.10 {
        return isClient;
        //?} else {
        return flow == PacketFlow.CLIENTBOUND;
        //?}
    }

    @Inject(method = "channelActive", at = @At("TAIL"))
    private void arcadium$observeChat(ChannelHandlerContext ctx, CallbackInfo ci) {
        if (arcadium$isClient()) ChatSendTrace.observe(channel);
    }

    //? if = 1.7.10 {
    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;[Lio/netty/util/concurrent/GenericFutureListener;)V", at = @At("HEAD"))
    private void arcadium$legacyChatPacket(Packet packet, GenericFutureListener[] listeners, CallbackInfo ci) {
        arcadium$recordChat(packet);
    }
    //?} else {
    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"))
    private void arcadium$chatPacket(Packet<?> packet, CallbackInfo ci) {
        arcadium$recordChat(packet);
    }

    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;Lio/netty/util/concurrent/GenericFutureListener;[Lio/netty/util/concurrent/GenericFutureListener;)V", at = @At("HEAD"))
    private void arcadium$chatPacketListeners(Packet<?> packet, GenericFutureListener<? extends Future<? super Void>> listener,
                                            GenericFutureListener<? extends Future<? super Void>>[] listeners, CallbackInfo ci) {
        arcadium$recordChat(packet);
    }

    //?}

    @Unique private void arcadium$recordChat(Packet<?> packet) {
        if (packet instanceof ChatMessageC2SPacket) {
            String message = ((ChatMessageC2SPacket) packet).getMessage();
            ChatSendTrace.packet(packet, message.length(), message.startsWith("/"), Minecraft.getInstance().isSameThread());
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void arcadium$chatTimingTick(CallbackInfo ci) {
        if (arcadium$isClient()) ChatSendTrace.poll();
    }
}
*///?}
