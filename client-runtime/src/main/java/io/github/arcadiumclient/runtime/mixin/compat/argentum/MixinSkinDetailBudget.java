// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.features.SkinDetailBudget;
import io.github.arcadiumclient.runtime.client.features.SkinLayersBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@IfModLoaded("legacy-skin-layers")
@Mixin(targets="org.polyfrost.skinlayers.PlayerLayerBinder",remap=false)
public abstract class MixinSkinDetailBudget {
    @Inject(method="bind",at=@At("HEAD"),cancellable=true,remap=false)
    private static void arcadium$detail(@Coerce Object model,@Coerce Object player,boolean slim,CallbackInfo callback) {
        if(!SkinDetailBudget.allow(player) && SkinLayersBridge.clearModel(model))callback.cancel();
    }
}
