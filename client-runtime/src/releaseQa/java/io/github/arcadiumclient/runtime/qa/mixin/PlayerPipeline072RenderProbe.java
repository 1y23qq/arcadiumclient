// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.qa.PlayerPipeline072Qa;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PlayerRenderer.class)
public abstract class PlayerPipeline072RenderProbe {
    @WrapMethod(method = "render(Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;DDDFF)V")
    private void arcadium$playerPath(ClientPlayerEntity player, double x, double y, double z, float yaw, float delta, Operation<Void> original) {
        var before = PlayerPipeline072Qa.beginPlayer(player);
        try { original.call(player, x, y, z, yaw, delta); }
        finally { PlayerPipeline072Qa.endPlayer(player, before); }
    }
}
