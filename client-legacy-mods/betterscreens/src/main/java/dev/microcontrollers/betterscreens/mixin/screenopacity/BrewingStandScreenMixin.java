package dev.microcontrollers.betterscreens.mixin.screenopacity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.microcontrollers.betterscreens.config.BetterScreensConfig;
import net.minecraft.client.gui.screen.inventory.menu.BrewingStandScreen;
import org.polyfrost.oneconfig.internal.legacy.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BrewingStandScreen.class)
public class BrewingStandScreenMixin {
    @WrapOperation(method = "renderMenuBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/inventory/menu/BrewingStandScreen;drawTexture(IIIIII)V"))
    private void wrapTexture(BrewingStandScreen instance, int i, int j, int k, int l, int m, int n, Operation<Void> original) {
        GlStateManager.enableAlphaTest();
        GlStateManager.enableBlend();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.color4f(1F, 1F, 1F, BetterScreensConfig.containerTextureOpacity / 100F);
        original.call(instance, i, j, k, l, m, n);
        GlStateManager.color4f(1F, 1F, 1F, 1F);
        GlStateManager.disableBlend();
    }
}
