// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.arcadiumclient.runtime.client.capes.LocalCape;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.entity.layer.CapeLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CapeLayer.class)
public abstract class LocalCapeLayerMixin {
    @WrapOperation(method = "render(Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;FFFFFFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;hasInfo()Z"))
    private boolean arcadium$localCapeWithoutPlayerInfo(ClientPlayerEntity player, Operation<Boolean> original) {
        return original.call(player) || LocalCape.activeFor(player.getUuid());
    }
}
