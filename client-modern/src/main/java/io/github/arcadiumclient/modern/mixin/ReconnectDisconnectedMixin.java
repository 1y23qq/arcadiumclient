// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import io.github.arcadiumclient.modern.gui.ReconnectTargets;
import io.github.arcadiumclient.shared.network.ReconnectCountdown;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.polyfrost.oneconfig.api.platform.v1.Platform;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DisconnectedScreen.class)
public abstract class ReconnectDisconnectedMixin extends Screen {
    @Shadow @Final private Screen parent;
    @Shadow @Final private LinearLayout layout;
    @Unique private final ReconnectCountdown arcadium$countdown = new ReconnectCountdown();
    @Unique private ReconnectTargets.Target arcadium$target;
    @Unique private Button arcadium$reconnect;

    protected ReconnectDisconnectedMixin(Component title) { super(title); }

    @Inject(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/layouts/LinearLayout;arrangeElements()V"))
    private void arcadium$button(CallbackInfo callback) {
        if (arcadium$target == null) arcadium$target = ReconnectTargets.last();
        if (arcadium$target == null || !minecraft.allowsMultiplayer()) return;
        arcadium$countdown.start();
        if (arcadium$reconnect == null) arcadium$reconnect = Button.builder(Component.literal(arcadium$countdown.label()), button -> arcadium$retry()).width(200).build();
        layout.addChild(arcadium$reconnect);
    }
    @Override public void tick() {
        super.tick();
        if (Platform.screen().current() != this || arcadium$reconnect == null) return;
        arcadium$reconnect.setMessage(Component.literal(arcadium$countdown.label()));
        if (arcadium$countdown.ready()) arcadium$retry();
    }
    @Unique private void arcadium$retry() {
        if (Platform.screen().current() != this || arcadium$target == null || !arcadium$countdown.take()) return;
        arcadium$reconnect.active = false;
        arcadium$target.connect(parent);
    }
    @Override public void removed() {
        arcadium$countdown.cancel(); ReconnectTargets.forget(arcadium$target); super.removed();
    }
}
