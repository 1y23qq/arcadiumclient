// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.ModernArmorAuxiliary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@IfModLoaded("argentum")
@Mixin(targets="dev.rdh.argentum.impl.render.entity.EntityShadowBatch",remap=false)
public abstract class MixinModernArmorAuxiliaryShadows {
    @Inject(method="flush",at=@At("HEAD"),remap=false)
    private void arcadium$modernBeforeShadows(@Coerce Object commands,CallbackInfo callback){ModernArmorAuxiliary.beforeShadows(commands);}
}
