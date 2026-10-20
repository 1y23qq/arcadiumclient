package dev.microcontrollers.overlaytweaks.mixin;

import dev.microcontrollers.overlaytweaks.config.OverlayTweaksConfig;
import net.minecraft.client.gui.GameGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameGui.class)
public class GameGuiMixin {
    @Inject(method = "renderPumpkinOverlay", at = @At("HEAD"), cancellable = true)
    private void cancelPumpkinOverlay(int width, int height, CallbackInfo ci) {
        if (OverlayTweaksConfig.equipableOpacity <= 0f) ci.cancel();
    }

    @ModifyArg(method = "renderPumpkinOverlay", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glColor4f(FFFF)V", ordinal = 0), index = 3)
    private float pumpkinOpacity(float alpha) {
        return alpha * Math.max(0f, Math.min(1f, OverlayTweaksConfig.equipableOpacity / 100f));
    }

    @Inject(method = "renderNauseaOverlay", at = @At("HEAD"), cancellable = true)
    private void cancelNauseaOverlay(float portalTime, int width, int height, CallbackInfo ci) {
        if (OverlayTweaksConfig.netherPortalOpacity <= 0f) ci.cancel();
    }

    @ModifyArg(method = "renderNauseaOverlay", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glColor4f(FFFF)V", ordinal = 0), index = 3)
    private float nauseaOpacity(float alpha) {
        return alpha * Math.max(0f, Math.min(1f, OverlayTweaksConfig.netherPortalOpacity / 100f));
    }
}
