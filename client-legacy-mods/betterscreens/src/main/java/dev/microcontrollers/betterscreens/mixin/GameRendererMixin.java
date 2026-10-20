package dev.microcontrollers.betterscreens.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.microcontrollers.betterscreens.config.BetterScreensConfig;
import dev.microcontrollers.betterscreens.hook.ContainerScaleHook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Window;
import org.polyfrost.oneconfig.internal.legacy.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.nio.FloatBuffer;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Shadow
    private Minecraft minecraft;

    @WrapOperation(method = "render(F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;render(IIF)V"))
    private void wrapScreenRender(Screen screen, int mouseX, int mouseY, float tickDelta, Operation<Void> original) {
        if (!ContainerScaleHook.shouldScale(screen)) {
            original.call(screen, mouseX, mouseY, tickDelta);
            return;
        }

        int previousScale = ContainerScaleHook.getNewScale();
        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        ContainerScaleHook.setNewScale(BetterScreensConfig.containerScale);
        Window scaled = new Window(this.minecraft, this.minecraft.width, this.minecraft.height);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0.0D, scaled.getScaledWidth(), scaled.getScaledHeight(), 0.0D, 1000.0D, 3000.0D);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.translatef(0.0F, 0.0F, -2000.0F);
        try {
            int scaledMouseX = Mouse.getX() * scaled.getWidth() / this.minecraft.width;
            int scaledMouseY = scaled.getHeight() - Mouse.getY() * scaled.getHeight() / this.minecraft.height - 1;
            original.call(screen, scaledMouseX, scaledMouseY, tickDelta);
        } finally {
            ContainerScaleHook.setNewScale(previousScale);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(previousMatrixMode);
        }
    }
}
