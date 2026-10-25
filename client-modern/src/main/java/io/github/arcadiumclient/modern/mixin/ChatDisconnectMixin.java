// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.modern.client.chat.ChatHistory;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Gui.class)
public abstract class ChatDisconnectMixin {
    @WrapMethod(method = "onDisconnected")
    private void arcadium$disconnect(Operation<Void> original) {
        ChatHistory.beginDisconnect();
        try { original.call(); } finally { ChatHistory.endDisconnect(); }
    }
}
