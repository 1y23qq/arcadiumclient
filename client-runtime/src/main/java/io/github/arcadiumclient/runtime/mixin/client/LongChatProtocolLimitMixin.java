// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.text.Formatting;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LocalClientPlayerEntity.class, priority = 900)
public abstract class LongChatProtocolLimitMixin {
    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void arcadium$serverMessageLimit(String message, CallbackInfo ci) {
        if (message == null || message.length() <= 100) return;
        ((LocalClientPlayerEntity) (Object) this).addMessage(new LiteralText("This server accepts messages up to 100 characters.")
                .setStyle(new Style().setColor(Formatting.RED)));
        ci.cancel();
    }
}
