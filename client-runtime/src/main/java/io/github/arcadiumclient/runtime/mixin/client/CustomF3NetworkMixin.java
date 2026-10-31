// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client;
import io.github.arcadiumclient.runtime.client.debug.LegacyDebugExtras;
import io.github.arcadiumclient.runtime.client.features.CustomF3Config;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Connection.class)
public abstract class CustomF3NetworkMixin {
    @Inject(method="doSend",at=@At("HEAD")) private void arcadium$sent(CallbackInfo callback){if(CustomF3Config.getEnabled())LegacyDebugExtras.SENT.increment();}
    @Inject(method="channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V",at=@At("HEAD")) private void arcadium$received(CallbackInfo callback){if(CustomF3Config.getEnabled())LegacyDebugExtras.RECEIVED.increment();}
}
