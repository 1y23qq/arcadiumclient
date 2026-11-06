// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.ResidentMaterial072Evidence;
import net.minecraft.client.render.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractTexture.class)
public abstract class ResidentMaterial072TextureProbe {
    @Inject(method="getGlId",at=@At("HEAD"))
    private void material072$get(CallbackInfoReturnable<Integer> callback){ResidentMaterial072Evidence.textureGetter();}
}
