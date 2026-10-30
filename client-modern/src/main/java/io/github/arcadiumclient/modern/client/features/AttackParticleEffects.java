// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.features;

import io.github.arcadiumclient.modern.mixin.PlayerSweepAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class AttackParticleEffects {
    public record Scope(AttackParticleState previous) { }
    private static final ThreadLocal<AttackParticleState> ACTIVE = new ThreadLocal<>();
    private static volatile AttackParticleState lastClient;
    private AttackParticleEffects() { }

    public static Scope begin(Player player, Entity target) {
        boolean charged = player.getAttackStrengthScale(0.5F) > 0.9F;
        PlayerSweepAccess access = (PlayerSweepAccess) player;
        boolean critical = charged && access.arcadium$canCriticalAttack(target);
        boolean sweep = access.arcadium$isSweepAttack(charged, critical, charged && player.isSprinting());
        AttackParticleState state = new AttackParticleState();
        state.begin(player, target, player.level(), sweep, System.nanoTime());
        Scope scope = new Scope(ACTIVE.get());
        ACTIVE.set(state);
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.player == player) lastClient = state;
        return scope;
    }

    public static void end(Scope scope) {
        if (scope.previous == null) ACTIVE.remove(); else ACTIVE.set(scope.previous);
    }

    public static void resolve(Player player, Entity target, boolean sweep) {
        AttackParticleState state = ACTIVE.get();
        if (state != null) state.resolve(player, target, player.level(), sweep);
    }

    public static boolean suppressExtraCritical(Player player, Entity target) {
        AttackParticleState active = ACTIVE.get(), delayed = lastClient;
        long now = System.nanoTime();
        return active != null && active.suppress(player, target, player.level(), now)
            || delayed != null && delayed.suppress(player, target, player.level(), now);
    }
}
