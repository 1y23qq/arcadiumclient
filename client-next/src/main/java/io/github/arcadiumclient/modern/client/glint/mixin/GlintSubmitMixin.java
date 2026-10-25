// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.glint.mixin;

import io.github.arcadiumclient.modern.client.glint.GlintItemData;
import io.github.arcadiumclient.modern.client.glint.ModernGlint;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ItemFeatureRenderer.Submit.class, ModelFeatureRenderer.Submit.class})
public class GlintSubmitMixin implements GlintItemData, io.github.arcadiumclient.modern.client.glint.GlintSubmitContext {
    @Unique private net.minecraft.world.item.ItemDisplayContext arcadium$context;
    public net.minecraft.world.item.ItemDisplayContext arcadium$glintContext() { return arcadium$context; }
    @Unique private Data arcadium$data = Data.EMPTY;
    public Data arcadium$glintData() { return arcadium$data; }
    public void arcadium$glintData(Data data) { arcadium$data = data; }
    @Inject(method = "<init>", at = @At("RETURN"))
    private void arcadium$capture(CallbackInfo callback) { arcadium$data = ModernGlint.current(); arcadium$context = ModernGlint.currentContext(); }
}
