// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Player.class)
public interface PlayerSweepAccess {
    @Invoker("canCriticalAttack") boolean arcadium$canCriticalAttack(Entity target);
    @Invoker("isSweepAttack") boolean arcadium$isSweepAttack(boolean charged, boolean critical, boolean knockback);
}
