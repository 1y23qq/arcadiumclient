// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.glint.mixin;

import io.github.arcadiumclient.modern.client.glint.*;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelResolver.class)
public class GlintResolveMixin {
    @Inject(method = "updateForTopItem", at = @At("TAIL"))
    private void arcadium$remember(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context, Level level, ItemOwner owner, int seed, CallbackInfo callback) {
        var data = ModernGlint.inspect(stack);
        ((GlintItemData) state).arcadium$glintData(data);
        if (!GlintConfig.INSTANCE.getEnabled()) return;
        if (context == ItemDisplayContext.GUI && data.potion() && GlintConfig.INSTANCE.getPotionGlint()) ((GlintLayerState) state).arcadium$shinyPotion();
        if (stack.hasFoil() || data.potion() && GlintConfig.INSTANCE.getPotionGlint()) state.setAnimated();
    }
}
