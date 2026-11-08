// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.GpuModelTransforms;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@IfModLoaded("argentum")
@Mixin(targets="dev.rdh.argentum.impl.render.entity.instancing.EntityInstancing",remap=false)
public abstract class MixinGpuPlayerFrame {
    @Inject(method="beginBatch",at=@At("HEAD"),remap=false)
    private void arcadium$visiblePlayers(CallbackInfoReturnable<Boolean> callback){GpuModelTransforms.beginFrame();}
}
