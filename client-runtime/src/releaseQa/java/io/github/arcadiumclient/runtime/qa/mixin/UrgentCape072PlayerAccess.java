// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.network.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientPlayerEntity.class)
public interface UrgentCape072PlayerAccess {
    @Accessor("player") void cape072$playerInfo(PlayerInfo info);
}
