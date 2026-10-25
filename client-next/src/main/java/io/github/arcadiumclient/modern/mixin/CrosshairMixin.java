// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import io.github.arcadiumclient.modern.client.features.CustomCrosshairConfig;
import io.github.arcadiumclient.modern.client.features.CustomCrosshairRenderer;
import io.github.arcadiumclient.modern.client.features.LegacyCrosshairTweaks;
import io.github.arcadiumclient.modern.client.features.LegacyCrosshairTweaksConfig;
import io.github.arcadiumclient.modern.client.features.LegacyCrosshairPolicy;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Hud.class, priority = 1100)
public abstract class CrosshairMixin {
    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void arcadium$custom(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        if (LegacyCrosshairTweaks.hidden() || CustomCrosshairRenderer.render(graphics)) ci.cancel();
    }
    @ModifyExpressionValue(method = "extractCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z"))
    private boolean arcadium$thirdPerson(boolean original) { return original || LegacyCrosshairTweaks.thirdPerson(CustomCrosshairConfig.getThirdPerson()); }
    @ModifyExpressionValue(method = "extractCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;canRenderCrosshairForSpectator(Lnet/minecraft/world/phys/HitResult;)Z"))
    private boolean arcadium$spectator(boolean original) { return original || LegacyCrosshairTweaks.active() && LegacyCrosshairTweaksConfig.showCrosshairInSpectator; }
    @WrapOperation(method = "extractCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V", ordinal = 0))
    private void arcadium$opacity(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height, Operation<Void> original) {
        if (!LegacyCrosshairTweaks.active()) { original.call(graphics, pipeline, sprite, x, y, width, height); return; }
        boolean removeBlending = LegacyCrosshairTweaks.removeBlending();
        graphics.blitSprite(removeBlending ? RenderPipelines.GUI_TEXTURED : pipeline, sprite, x, y, width, height, LegacyCrosshairPolicy.tint(LegacyCrosshairTweaks.alpha(), removeBlending));
    }
}
