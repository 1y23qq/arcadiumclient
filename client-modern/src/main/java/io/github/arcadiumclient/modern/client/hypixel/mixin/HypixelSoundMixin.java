// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.hypixel.mixin;

import io.github.arcadiumclient.modern.client.hypixel.ModernHypixel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundManager.class)
public abstract class HypixelSoundMixin {
    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void arcadium$silentLobby(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> callback) {
        if (sound != null && ModernHypixel.muteSound(sound.getIdentifier().toString())) callback.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
    }
}
