// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.glint.mixin;

import io.github.arcadiumclient.modern.client.glint.GlintConfig;
import java.util.List;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "org.polyfrost.overflowanimations.handler.rendering.pipeline.ArmorGlint", remap = false)
public class OverflowArmorGlintMixin {
    @Inject(method = "layers", at = @At("HEAD"), cancellable = true)
    private static void arcadium$colorizerOwnsArmor(CallbackInfoReturnable<List<RenderType>> callback) {
        if (GlintConfig.INSTANCE.getEnabled()) callback.setReturnValue(List.of());
    }
}
