// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import io.github.arcadiumclient.modern.client.BadgeText;
import io.github.arcadiumclient.modern.client.presence.ArcadiumPlayersConfig;
import io.github.arcadiumclient.modern.client.presence.ArcadiumPresence;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class NametagBadgeMixin {
    @Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
    private void arcadium$badge(Entity entity, CallbackInfoReturnable<Component> cir) {
        if (cir.getReturnValue() != null && entity instanceof Player && ArcadiumPlayersConfig.getShowNametag() && ArcadiumPresence.INSTANCE.isOnline(entity.getUUID()))
            cir.setReturnValue(BadgeText.prefix(cir.getReturnValue()));
    }
}
