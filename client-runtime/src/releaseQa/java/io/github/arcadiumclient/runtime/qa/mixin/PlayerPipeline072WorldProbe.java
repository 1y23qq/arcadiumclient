// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.qa.PlayerPipeline072Qa;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GameRenderer.class)
public abstract class PlayerPipeline072WorldProbe {
    @WrapMethod(method = "renderWorld(FJ)V")
    private void arcadium$playerWorld(float delta, long deadline, Operation<Void> original) {
        PlayerPipeline072Qa.beginWorld();
        original.call(PlayerPipeline072Qa.delta(delta), deadline);
        PlayerPipeline072Qa.worldRendered();
    }
}
