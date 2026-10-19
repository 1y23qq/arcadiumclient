package org.polyfrost.oneconfig.internal.mixin.render;

//? if = 1.8.9 {
/*import net.minecraft.client.render.TextRenderer;
import net.minecraft.resource.Identifier;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.polyfrost.oneconfig.internal.ui.compose.LegacyGameFont;
import org.polyfrost.oneconfig.internal.FontPreferences;
import org.polyfrost.compose.mc.MinecraftTextStyle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TextRenderer.class, priority = 900)
public abstract class Mixin_GameFont {
    @Shadow private float x;
    @Shadow private float y;
    @Shadow private boolean unicode;
    @Shadow @Final private Identifier fontLocation;
    @Shadow public abstract int getWidth(char character);
    @Unique private boolean arcadium$shadow;

    @Unique private boolean arcadium$defaultFont() {
        return fontLocation.getNamespace().equals("minecraft") && fontLocation.getPath().equals("textures/font/ascii.png");
    }

    @WrapMethod(method = "drawLayer(Ljava/lang/String;Z)V")
    private void arcadium$shadowLayer(String text, boolean shadow, Operation<Void> original) {
        boolean previous = arcadium$shadow;
        arcadium$shadow = shadow;
        try { original.call(text, shadow); }
        finally { arcadium$shadow = previous; }
    }

    @Inject(method = "drawGlyph", at = @At("HEAD"), cancellable = true)
    private void arcadium$drawFont(char character, boolean italic, CallbackInfoReturnable<Float> callback) {
        if (arcadium$defaultFont() && LegacyGameFont.handles(character)) {
            float correction = arcadium$shadow ? LegacyGameFont.shadowCorrection(character, unicode) : 0f;
            callback.setReturnValue(LegacyGameFont.draw(character, italic, unicode, x + correction, y + correction));
        }
    }

    @Inject(method = "getWidth(C)I", at = @At("HEAD"), cancellable = true)
    private void arcadium$fontWidth(char character, CallbackInfoReturnable<Integer> callback) {
        if (arcadium$defaultFont() && LegacyGameFont.handles(character)) callback.setReturnValue(LegacyGameFont.width(character, unicode));
    }

    @Inject(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), cancellable = true)
    private void arcadium$formattedWidth(String text, CallbackInfoReturnable<Integer> callback) {
        if (!arcadium$defaultFont() || FontPreferences.nativeSource() == FontPreferences.Source.RESOURCE_PACK || text == null) return;
        MinecraftTextStyle style = new MinecraftTextStyle(-1);
        int width = 0;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '§' && index + 1 < text.length()) {
                style.apply(text.charAt(++index));
                continue;
            }
            int advance = getWidth(character);
            width += Math.max(0, advance) + (style.getBold() && advance > 0 ? 1 : 0);
        }
        callback.setReturnValue(width);
    }
}
*///?}
