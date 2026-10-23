// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import org.polyfrost.oneconfig.internal.emoji.EmojiText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = LocalClientPlayerEntity.class, priority = 1100)
public abstract class EmojiSendMixin {
    @ModifyVariable(method = "sendChat", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String arcadium$shortcodesOnWire(String message) { return EmojiText.toWire(message); }
}
