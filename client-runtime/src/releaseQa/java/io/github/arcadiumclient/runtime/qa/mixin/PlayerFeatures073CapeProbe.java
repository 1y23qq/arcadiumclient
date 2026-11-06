// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import io.github.arcadiumclient.runtime.qa.PlayerFeatures073Qa;
import net.minecraft.client.render.model.entity.PlayerModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PlayerModel.class)
public abstract class PlayerFeatures073CapeProbe {
    @Inject(method="renderCape",at=@At("HEAD")) private void features073$cape(float scale,CallbackInfo callback){PlayerFeatures073Qa.cape();}
}
