package org.polyfrost.oneconfig.internal.mixin.render;

//? if <= 1.8.9 {
/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.resource.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.texture.DynamicTexture;
import net.minecraft.client.render.texture.TextureManager;
import java.lang.reflect.Method;
import org.polyfrost.oneconfig.internal.FontPreferences;
import org.polyfrost.oneconfig.internal.TextOptimizationOptions;
import org.polyfrost.oneconfig.internal.ui.compose.LegacyGameFont;
import org.polyfrost.oneconfig.internal.ui.compose.VanillaGlyphSink;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.rdh.argentum.impl.render.gui.TextBatcher", remap = false)
public abstract class Mixin_ArgentumFontSource implements VanillaGlyphSink {
    @Unique private boolean arcadium$defaultFont;
    @Unique private boolean arcadium$unicode;
    @Shadow private boolean batching;
    @Shadow protected abstract void useTexture(TextureManager textures, Identifier texture);
    @Shadow protected abstract void quad(float x0, float y0, float u0, float v0, float x1, float y1, float u1, float v1,
        float x2, float y2, float u2, float v2, float x3, float y3, float u3, float v3);
    @Shadow protected abstract void flushElementBatch(TextureManager textures);
    @Shadow public abstract void clearCaches();
    @Unique private static Method arcadium$capturingNames;
    @Unique private static Method arcadium$flushNames;

    @Inject(method = "readWidths", at = @At("HEAD"))
    private void arcadium$trackFont(CallbackInfo callback, @Local(argsOnly = true, ordinal = 0) Identifier font) {
        arcadium$defaultFont = font.getNamespace().equals("minecraft") && font.getPath().equals("textures/font/ascii.png");
    }

    @ModifyExpressionValue(method = {"begin", "shouldFlushBeforeImmediate"}, at = @At(value = "FIELD",
        target = "Ldev/rdh/argentum/impl/config/ArgentumConfig;fontBatching:Z"))
    private boolean arcadium$selectedFont(boolean enabled, @Local(argsOnly = true) String text) {
        if (!enabled || !arcadium$defaultFont) return enabled;
        if (TextOptimizationOptions.immediateText()) return false;
        FontPreferences.Source source = FontPreferences.nativeSource();
        return source == FontPreferences.Source.RESOURCE_PACK || (source == FontPreferences.Source.VANILLA
            && TextOptimizationOptions.vanillaFontBatching && text != null && LegacyGameFont.cacheableMetrics(text));
    }

    @Override public boolean arcadium$vanillaQuad(DynamicTexture texture, float x, float y, float width, float height,
            float u0, float v0, float u1, float v1, boolean italic) {
        if (!batching || !arcadium$defaultFont || FontPreferences.nativeSource() != FontPreferences.Source.VANILLA) return false;
        useTexture(Minecraft.getInstance().getTextureManager(), LegacyGameFont.batchTexture(texture));
        float skew = italic ? 1f : 0f;
        quad(x + skew, y, u0, v0, x - skew, y + height, u0, v1,
            x + width + skew, y, u1, v0, x + width - skew, y + height, u1, v1);
        return true;
    }

    @Override public void arcadium$flushBeforeImmediate() {
        flushElementBatch(Minecraft.getInstance().getTextureManager());
        try {
            if (arcadium$capturingNames == null) {
                Class<?> type = Class.forName("dev.rdh.argentum.impl.render.entity.NameTagBatch");
                arcadium$capturingNames = type.getMethod("capturing"); arcadium$flushNames = type.getMethod("flush");
            }
            Object names = arcadium$capturingNames.invoke(null);
            if (names != null) arcadium$flushNames.invoke(names);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Could not flush native text batches", failure); }
    }

    @Override public void arcadium$unicode(boolean value) {
        if (arcadium$unicode != value) { arcadium$unicode = value; clearCaches(); }
    }
}
*///?}
