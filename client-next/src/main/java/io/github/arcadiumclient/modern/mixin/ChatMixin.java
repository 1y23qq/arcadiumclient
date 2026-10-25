// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import io.github.arcadiumclient.modern.client.chat.ChatHistory;
import io.github.arcadiumclient.modern.client.chat.ChatTweaks;
import io.github.arcadiumclient.modern.client.features.ChatTweaksConfig;
import io.github.arcadiumclient.modern.client.features.ChatFiltersConfig;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatMixin {
    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V", at = @At("HEAD"), cancellable = true)
    private void arcadium$message(Component text, MessageSignature signature, net.minecraft.client.multiplayer.chat.GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
        if (ChatTweaks.intercept((ChatComponent) (Object) this, text, signature, source, tag)) ci.cancel();
    }
    @ModifyConstant(method = {"addMessageToQueue", "addMessageToDisplayQueue", "addRecentChat"}, constant = @Constant(intValue = 100))
    private int arcadium$historyLimit(int vanilla) { return Math.max(100, Math.min(16384, ChatTweaksConfig.getIncreaseChatHistoryLimit())); }
    @Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"), cancellable = true)
    private void arcadium$filterExisting(GuiMessage message, CallbackInfo ci) {
        if (ChatFiltersConfig.hide(message.content().getString())) ci.cancel();
    }
    @Inject(method = "clearMessages", at = @At("HEAD"), cancellable = true)
    private void arcadium$preserve(boolean clearInput, CallbackInfo ci) {
        if (ChatHistory.keepOnDisconnect()) ci.cancel();
        else ChatTweaks.clear();
    }
    @Inject(method = "clearMessages", at = @At("RETURN"))
    private void arcadium$cleared(boolean clearInput, CallbackInfo ci) { ChatHistory.changed(); }
}
