package org.polyfrost.oneconfig.internal.mixin.render;

//? if <= 1.8.9 {
/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.resource.Identifier;
import org.polyfrost.oneconfig.internal.FontPreferences;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.rdh.argentum.impl.render.gui.TextBatcher", remap = false)
public abstract class Mixin_ArgentumFontSource {
    @Unique private boolean arcadium$defaultFont;

    @Inject(method = "readWidths", at = @At("HEAD"))
    private void arcadium$trackFont(CallbackInfo callback, @Local(argsOnly = true, ordinal = 0) Identifier font) {
        arcadium$defaultFont = font.getNamespace().equals("minecraft") && font.getPath().equals("textures/font/ascii.png");
    }

    @ModifyExpressionValue(method = {"begin", "shouldFlushBeforeImmediate"}, at = @At(value = "FIELD",
        target = "Ldev/rdh/argentum/impl/config/ArgentumConfig;fontBatching:Z"))
    private boolean arcadium$selectedFont(boolean enabled) {
        return enabled && (!arcadium$defaultFont || FontPreferences.nativeSource() == FontPreferences.Source.RESOURCE_PACK);
    }
}
*///?}
