// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.modern.client.features.AttackParticleEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = Player.class, priority = 1500)
public class AttackParticleContextMixin {
    @WrapMethod(method = "attack")
    private void arcadium$attackContext(Entity target, Operation<Void> original) {
        AttackParticleEffects.Scope scope = AttackParticleEffects.begin((Player) (Object) this, target);
        try { original.call(target); }
        finally { AttackParticleEffects.end(scope); }
    }

    @ModifyExpressionValue(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isSweepAttack(ZZZ)Z"))
    private boolean arcadium$actualSweepDecision(boolean sweep, Entity target) {
        AttackParticleEffects.resolve((Player) (Object) this, target, sweep);
        return sweep;
    }
}
