package org.polyfrost.oneconfig.internal.mixin.events;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.polyfrost.oneconfig.api.event.v1.EventManager;
import org.polyfrost.oneconfig.api.event.v1.events.ChatEvent;
import org.spongepowered.asm.mixin.Mixin;

//? if > 1.8.9 {
import net.minecraft.client.multiplayer.ClientPacketListener;
//?}

//? if <= 1.8.9 {
/*//? if = 1.7.10 {
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
//?} else {
import net.minecraft.client.player.LocalPlayer;
//?}
import net.minecraft.client.Minecraft;
import org.polyfrost.oneconfig.internal.legacy.command.ClientCommandInternals;
import org.polyfrost.oneconfig.internal.network.ChatSendTrace;
*///?}

//~ if = 1.7.10 'ClientPacketListener' -> 'LocalClientPlayerEntity' {
//~ if = 1.8.9 'ClientPacketListener' -> 'LocalPlayer'
@Mixin(ClientPacketListener.class)
//~}
public abstract class Mixin_ChatSendEvent {
    @WrapMethod(method = "sendChat")
    private void ocfg$sendChat(String message, Operation<Void> original) {
        //? if <= 1.8.9 {
        /*try (ChatSendTrace.Attempt trace = ChatSendTrace.send(message.length(), message.startsWith("/"), Minecraft.getInstance().isSameThread())) {
        *///?}
        ChatEvent.Send event = new ChatEvent.Send(message);
        EventManager.INSTANCE.post(event);
        //? if <= 1.8.9
        //trace.eventReturned();
        //? if <= 1.8.9
        //if (event.cancelled) trace.cancelled();
        if (event.cancelled) return;

        //? if <= 1.8.9 {
        /*String commandMessage = event.message;
        if (commandMessage.startsWith("/") && ClientCommandInternals.executeCommand(commandMessage.substring(1))) {
            trace.handledLocally();
            return;
        }
        trace.nativeCall();
        *///?}
        original.call(event.message);
        //? if <= 1.8.9
        //}
    }
}
