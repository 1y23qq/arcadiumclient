package io.github.arcadiumclient.featuretest.mixin;
import io.github.arcadiumclient.featuretest.FeatureQa;

import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.gui.GameGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiElement.class)
public abstract class CrosshairProbeMixin {
    @Inject(method = "drawTexture(IIIIII)V", at = @At("HEAD"))
    private void arcadium$crosshairMatrix(int x, int y, int u, int v, int width, int height, CallbackInfo callback) {
        if ((Object) this instanceof GameGui && u == 0 && v == 0 && width == 16 && height == 16) {
            FeatureQa.crosshairMatrix();
            io.github.arcadiumclient.featuretest.Release040WorldQa.vanillaCrosshairDraw();
        }
    }
}
