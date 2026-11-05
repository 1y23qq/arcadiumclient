// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.ModernArmorGlintPipeline;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@IfModLoaded("argentum")
@Mixin(targets = "dev.rdh.argentum.impl.render.entity.instancing.EntityCapture", remap = false)
public abstract class MixinModernArmorCapture {
    @Inject(method = "setTexture", at = @At("TAIL"), remap = false)
    private void arcadium$modernPass(Identifier texture, CallbackInfo callback) { ModernArmorGlintPipeline.textureBound(this, texture); }
}
