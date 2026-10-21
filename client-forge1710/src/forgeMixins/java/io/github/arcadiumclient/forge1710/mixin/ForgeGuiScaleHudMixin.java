// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.client.features.LegacyGuiScaleTransform;
import io.github.arcadiumclient.runtime.client.features.NoRender;
import io.github.arcadiumclient.runtime.client.features.NoRenderPolicy;
import io.github.arcadiumclient.runtime.legacy.LegacyWindows;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraftforge.client.GuiIngameForge")
public abstract class ForgeGuiScaleHudMixin {
    @WrapMethod(method = "renderHotbar(IIF)V", remap = false)
    private void arcadium$hotbar(int width, int height, float partialTicks, Operation<Void> original) {
        boolean pushed = LegacyGuiScaleTransform.hotbar(LegacyWindows.current());
        try { original.call(width, height, partialTicks); }
        finally { LegacyGuiScaleTransform.pop(pushed); }
    }

    @WrapMethod(method = {"renderArmor(II)V", "renderAir(II)V", "renderHealth(II)V", "renderFood(II)V", "renderExperience(II)V",
        "renderJumpBar(II)V", "renderToolHightlight(II)V", "renderHealthMount(II)V"}, remap = false)
    private void arcadium$status(int width, int height, Operation<Void> original) {
        boolean pushed = LegacyGuiScaleTransform.hotbar(LegacyWindows.current());
        try { original.call(width, height); }
        finally { LegacyGuiScaleTransform.pop(pushed); }
    }

    @Inject(method = "renderHelmet", at = @At("HEAD"), cancellable = true, remap = false)
    private void arcadium$helmet(CallbackInfo callback) {
        if (NoRender.hide(NoRenderPolicy.Effect.PUMPKIN_OVERLAY)) callback.cancel();
    }

    @Inject(method = "renderPortal", at = @At("HEAD"), cancellable = true, remap = false)
    private void arcadium$portal(CallbackInfo callback) {
        if (NoRender.hide(NoRenderPolicy.Effect.PORTAL_OVERLAY)) callback.cancel();
    }
}
