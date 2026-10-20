//~ gui_graphics
package org.polyfrost.oneconfig.internal.mixin.skia;

//? if > 1.7.10 {
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.polyfrost.oneconfig.internal.ui.hud.DebugOverlayOffscreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if > 1.8.9 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?}

//? if <= 1.8.9 {
/*import net.minecraft.client.render.Window;
*///?}

//~ if <= 1.8.9 'DebugScreenOverlay' -> 'DebugOverlay'
@Mixin(DebugScreenOverlay.class)
public class Mixin_DebugOverlayAboveUi {
    //~ if >= 26.1 'render' -> 'extractRenderState'
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    //~ if <= 1.8.9 'GuiGraphicsExtractor ctx' -> 'Window window'
    private void oneconfig$deferDebugOverlay(GuiGraphicsExtractor ctx, CallbackInfo ci) {
        //~ if <= 1.8.9 'shouldSuppressVanilla()' -> 'shouldSuppressVanilla((DebugOverlay) (Object) this, window)'
        if (DebugOverlayOffscreen.INSTANCE.shouldSuppressVanilla()) {
            ci.cancel();
        }
    }
}
//? } else {
/*import net.minecraft.client.gui.GameGui;
import net.minecraft.client.render.TextRenderer;
import org.polyfrost.oneconfig.internal.ui.hud.DebugOverlayOffscreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameGui.class)
public class Mixin_DebugOverlayAboveUi {
    @Inject(method = "render(FZII)V", at = @At("HEAD"))
    private void oneconfig$beginDebugFrame(float tickDelta, boolean mouseGrabbed, int mouseX, int mouseY, CallbackInfo callback) {
        DebugOverlayOffscreen.INSTANCE.beginLegacyDebugFrame();
    }

    @Redirect(method = "render(FZII)V",
        slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;debugEnabled:Z"),
            to = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/GameGui;overlayMessageCooldown:I", ordinal = 0)),
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/TextRenderer;drawWithShadow(Ljava/lang/String;III)I"))
    private int oneconfig$deferDebugShadow(TextRenderer font, String text, int x, int y, int color) {
        if (DebugOverlayOffscreen.INSTANCE.captureLegacyDebugText(font, text, x, y, color)) return 0;
        return font.drawWithShadow(text, x, y, color);
    }

    @Redirect(method = "render(FZII)V",
        slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;debugEnabled:Z"),
            to = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/GameGui;overlayMessageCooldown:I", ordinal = 0)),
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GameGui;drawString(Lnet/minecraft/client/render/TextRenderer;Ljava/lang/String;III)V"))
    private void oneconfig$deferDebugLine(GameGui gui, TextRenderer font, String text, int x, int y, int color) {
        if (!DebugOverlayOffscreen.INSTANCE.captureLegacyDebugText(font, text, x, y, color)) gui.drawString(font, text, x, y, color);
    }
}
*///? }
