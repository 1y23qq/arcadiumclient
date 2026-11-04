// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client;

import io.github.arcadiumclient.runtime.client.gui.ReconnectTargets;
import io.github.arcadiumclient.shared.network.ReconnectCountdown;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.menu.multiplayer.DisconnectedScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
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
    @Unique private final ReconnectCountdown arcadium$countdown = new ReconnectCountdown();
    @Unique private ReconnectTargets.Target arcadium$target;
    @Unique private ButtonWidget arcadium$reconnect;

    @Inject(method = "init", at = @At("TAIL"))
    private void arcadium$button(CallbackInfo callback) {
        if (arcadium$target == null) arcadium$target = ReconnectTargets.last();
        if (arcadium$target == null) return;
        ButtonWidget back = null;
        for (Object candidate : buttons) if (candidate instanceof ButtonWidget button && button.id == 0) { back = button; break; }
        if (back == null) return;
        back.y = Math.min(back.y, Math.max(8, height - 52));
        arcadium$countdown.start();
        arcadium$reconnect = new ButtonWidget(37071, back.x, back.y + 24, back.getWidth(), 20, arcadium$countdown.label());
        buttons.add(arcadium$reconnect);
    }

    @Override public void tick() {
        super.tick();
        if (minecraft.screen != (Object) this || arcadium$reconnect == null) return;
        arcadium$reconnect.message = arcadium$countdown.label();
        if (arcadium$countdown.ready()) arcadium$retry();
    }

    @Inject(method = "buttonClicked", at = @At("HEAD"), cancellable = true)
    private void arcadium$click(ButtonWidget button, CallbackInfo callback) {
        if (button == arcadium$reconnect) { callback.cancel(); arcadium$retry(); }
        else if (button.id == 0) { arcadium$countdown.cancel(); ReconnectTargets.forget(arcadium$target); }
    }

    @Unique private void arcadium$retry() {
        if (minecraft.screen != (Object) this || arcadium$target == null || !arcadium$countdown.take()) return;
        arcadium$reconnect.active = false;
        arcadium$target.connect(parent);
    }
    @Override public void removed() {
        arcadium$countdown.cancel(); ReconnectTargets.forget(arcadium$target); super.removed();
    }
}
