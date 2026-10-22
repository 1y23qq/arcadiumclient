// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.screen.menu.multiplayer.ConnectScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public abstract class MixinConnectingServerCaption {
    @Unique private String arcadium$serverAddress = "";

    @Inject(method = "connect", at = @At("HEAD"))
    private void arcadium$destination(String host, int port, CallbackInfo callback) {
        String address = host.indexOf(':') >= 0 && !host.startsWith("[") ? "[" + host + "]" : host;
        arcadium$serverAddress = port == 25565 ? address : address + ":" + port;
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/resource/language/I18n;translate(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;"))
    private String arcadium$caption(String key, Object[] arguments, Operation<String> original) {
        if (key.equals("connect.connecting")) return "Connecting to " + arcadium$serverAddress + "...";
        if (key.equals("connect.authorizing")) return "Logging in to " + arcadium$serverAddress + "...";
        return original.call(key, arguments);
    }
}
