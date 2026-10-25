// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.modcompat.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "de.maxhenkel.voicechat.voice.client.ChatUtils", remap = false)
public class VoiceChatMessageMixin {
    @Inject(method = "sendPlayerMessage", at = @At("HEAD"), cancellable = true)
    private static void arcadium$displayOnClientThread(Component message, CallbackInfo callback) {
        var client = Minecraft.getInstance();
        if (client.isSameThread()) return;
        var captured = message.copy();
        client.execute(() -> { if (client.gui != null) client.gui.hud.getChat().addClientSystemMessage(captured); });
        callback.cancel();
    }
}
