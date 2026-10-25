// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import io.github.arcadiumclient.modern.client.BadgeText;
import io.github.arcadiumclient.modern.client.presence.ArcadiumPlayersConfig;
import io.github.arcadiumclient.modern.client.presence.ArcadiumPresence;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public abstract class TabBadgeMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void arcadium$badge(PlayerInfo player, CallbackInfoReturnable<Component> cir) {
        if (ArcadiumPlayersConfig.getShowTab() && ArcadiumPresence.INSTANCE.isOnline(player.getProfile().id()))
            cir.setReturnValue(BadgeText.prefix(cir.getReturnValue()));
    }
}
