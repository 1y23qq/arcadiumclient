// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.UrgentCape072Qa;
import net.minecraft.client.render.model.entity.PlayerModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class UrgentCape072LayerProbe {
    @Inject(method = "renderCape", at = @At("HEAD"))
    private void cape072$model(float scale, CallbackInfo callback) { UrgentCape072Qa.capeModelObserved(); }
}
