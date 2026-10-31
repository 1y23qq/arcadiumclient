// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.features.Skin3DConfig;
import io.github.arcadiumclient.runtime.client.features.SkinLayersBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@IfModLoaded("argentum")
@Mixin(targets = "dev.rdh.argentum.impl.render.entity.instancing.EntityCapture", remap = false)
public abstract class MixinSkinLayersInstancing {
    @Shadow private boolean suppressFixedFunction;
    @Inject(method = "record", at = @At("HEAD"), cancellable = true, remap = false)
    private void arcadium$voxelSkins(@Coerce Object part, float scale, CallbackInfoReturnable<Boolean> callback) {
        if (!suppressFixedFunction && Skin3DConfig.getEnabled() && SkinLayersBridge.hasMesh(part))
            callback.setReturnValue(false);
    }
}
