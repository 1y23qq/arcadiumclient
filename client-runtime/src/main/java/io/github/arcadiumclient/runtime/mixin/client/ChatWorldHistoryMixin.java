// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.arcadiumclient.runtime.client.chat.ChatHistory;
import io.github.arcadiumclient.runtime.client.chat.ChatHistoryAccess;
import io.github.arcadiumclient.runtime.client.features.ChatTweaksConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.chat.ChatGui;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class ChatWorldHistoryMixin {
    @WrapOperation(method = "openScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/chat/ChatGui;clear()V"))
    private void arcadium$disconnectHistory(ChatGui chat, Operation<Void> original) {
        ChatHistory.saveBeforeClear((ChatHistoryAccess) chat);
        if (ChatTweaksConfig.getDontClearChatHistory()) chat.resetScroll();
        else original.call(chat);
    }

    @Inject(method = "setWorld(Lnet/minecraft/client/world/ClientWorld;Ljava/lang/String;)V", at = @At("HEAD"))
    private void arcadium$saveOnDisconnect(ClientWorld world, String reason, CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        if (world == null && client.gui != null) {
            ChatGui chat = client.gui.getChat();
            ChatHistory.saveBeforeClear((ChatHistoryAccess) chat);
            if (!ChatTweaksConfig.getDontClearChatHistory()) chat.clear();
        }
    }

    @Inject(method = "shutdown", at = @At("HEAD"))
    private void arcadium$flushHistory(CallbackInfo ci) { ChatHistory.close(); }
}
