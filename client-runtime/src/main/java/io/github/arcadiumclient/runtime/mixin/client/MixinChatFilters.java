// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import net.minecraft.client.gui.ChatMessage;
import net.minecraft.client.gui.chat.ChatGui;
import net.minecraft.text.Text;
import io.github.arcadiumclient.runtime.client.features.ChatFiltersConfig;
import io.github.arcadiumclient.runtime.client.features.ChatFilterPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatGui.class)
public abstract class MixinChatFilters {
    @Shadow @Final private List<ChatMessage> messages;
    @Shadow public abstract void removeMessage(int id);

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;IIZ)V", at = @At("HEAD"), cancellable = true)
    private void arcadium$filterVisibleMessage(Text message, int id, int createdAt, boolean refreshing, CallbackInfo ci) {
        if (message == null || !ChatFiltersConfig.hide(message.getString())) return;
        if (!refreshing) {
            int limit = ChatFilterPolicy.historyLimit(messages.size());
            if (id != 0) removeMessage(id);
            messages.add(0, new ChatMessage(createdAt, message, id));
            while (messages.size() > limit) messages.remove(messages.size() - 1);
        }
        ci.cancel();
    }

    @WrapOperation(
            method = "addMessage(Lnet/minecraft/text/Text;IIZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/chat/ChatGui;removeMessage(I)V")
    )
    private void arcadium$keepHistoryOnRefresh(ChatGui chat, int id, Operation<Void> original,
                                             @Local(argsOnly = true) boolean refreshing) {
        if (!refreshing) original.call(chat, id);
    }
}
