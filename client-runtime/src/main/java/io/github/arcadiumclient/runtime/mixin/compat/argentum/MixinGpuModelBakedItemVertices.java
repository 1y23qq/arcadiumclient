// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.ForgeBakedItemVertices;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@IfModLoaded("argentum")
@Mixin(targets="dev.rdh.argentum.impl.render.entity.instancing.BakedItemGeometryCache$BakedItemGeometry",remap=false)
public abstract class MixinGpuModelBakedItemVertices {
    @ModifyExpressionValue(method="putQuads",at=@At(value="INVOKE",target="Ljava/util/Iterator;next()Ljava/lang/Object;"),remap=false)
    private static Object arcadium$materializeItemQuad(Object quad){return ForgeBakedItemVertices.consume(quad);}
}
