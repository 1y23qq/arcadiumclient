// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client;

import io.github.arcadiumclient.runtime.client.gui.ReconnectTargets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.menu.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.options.ServerListEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public abstract class ReconnectConnectMixin {
    @Inject(method = "<init>(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/options/ServerListEntry;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/menu/multiplayer/ConnectScreen;connect(Ljava/lang/String;I)V"))
    private void arcadium$server(Screen parent, Minecraft client, ServerListEntry server, CallbackInfo callback) { ReconnectTargets.remember(server); }

    @Inject(method = "<init>(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/client/Minecraft;Ljava/lang/String;I)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/menu/multiplayer/ConnectScreen;connect(Ljava/lang/String;I)V"))
    private void arcadium$address(Screen parent, Minecraft client, String host, int port, CallbackInfo callback) { ReconnectTargets.remember(host, port); }

    @Inject(method = "buttonClicked", at = @At("HEAD"))
    private void arcadium$cancel(ButtonWidget button, CallbackInfo callback) { if (button.id == 0) ReconnectTargets.clear(); }
}
