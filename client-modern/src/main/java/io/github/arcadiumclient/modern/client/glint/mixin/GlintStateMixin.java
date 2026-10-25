// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.glint.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.arcadiumclient.modern.client.glint.*;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.*;

@Mixin(ItemStackRenderState.class)
public abstract class GlintStateMixin implements GlintItemData, GlintLayerState {
    @Unique private Data arcadium$data = Data.EMPTY;
    @Shadow private int activeLayerCount;
    @Shadow private ItemStackRenderState.LayerRenderState[] layers;
    @Shadow ItemDisplayContext displayContext;
    public Data arcadium$glintData() { return arcadium$data; }
    public void arcadium$glintData(Data data) { arcadium$data = data; }
    public void arcadium$shinyPotion() {
        for (int i = 0; i < activeLayerCount; i++) layers[i].setFoilType(GlintConfig.INSTANCE.getPotionGlintForeground() ? ItemStackRenderState.FoilType.NONE : ItemStackRenderState.FoilType.STANDARD);
    }
    @WrapMethod(method = "submit")
    private void arcadium$capture(PoseStack pose, SubmitNodeCollector collector, int light, int overlay, int outline, Operation<Void> original) {
        var previous = ModernGlint.scope(arcadium$data);
        var previousContext = ModernGlint.scopeContext(displayContext);
        try { original.call(pose, collector, light, overlay, outline); }
        finally { ModernGlint.scope(previous); ModernGlint.scopeContext(previousContext); }
    }
}
