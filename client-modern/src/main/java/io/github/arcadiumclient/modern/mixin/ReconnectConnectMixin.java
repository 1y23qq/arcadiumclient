// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.arcadiumclient.modern.gui.ReconnectTargets;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import org.polyfrost.oneconfig.api.platform.v1.Platform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public abstract class ReconnectConnectMixin {
    @Inject(method = "startConnecting", at = @At("TAIL"))
    private static void arcadium$remember(CallbackInfo callback, @Local(argsOnly = true) ServerData server) {
        if (Platform.screen().current() instanceof ConnectScreen) ReconnectTargets.remember(server);
    }
    @WrapOperation(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/Button;builder(Lnet/minecraft/network/chat/Component;Lnet/minecraft/client/gui/components/Button$OnPress;)Lnet/minecraft/client/gui/components/Button$Builder;"))
    private Button.Builder arcadium$cancel(Component label, Button.OnPress action, Operation<Button.Builder> original) {
        return original.call(label, (Button.OnPress) button -> { ReconnectTargets.clear(); action.onPress(button); });
    }
}
