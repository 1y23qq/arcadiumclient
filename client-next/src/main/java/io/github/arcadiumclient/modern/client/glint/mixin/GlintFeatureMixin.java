// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.glint.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.modern.client.glint.GlintItemData;
import io.github.arcadiumclient.modern.client.glint.ModernGlint;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemFeatureRenderer.class)
public class GlintFeatureMixin {
    @WrapMethod(method = "prepareSubmit")
    private void arcadium$restore(ItemFeatureRenderer.Submit submit, boolean foil, Operation<Void> original) {
        var previous = ModernGlint.scope(((GlintItemData) (Object) submit).arcadium$glintData());
        var previousContext = ModernGlint.scopeContext(submit.displayContext());
        try { original.call(submit, foil); }
        finally { ModernGlint.scope(previous); ModernGlint.scopeContext(previousContext); }
    }
}
