// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.internal.mixin.render;

//? if <= 1.8.9 {
/*import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.polyfrost.oneconfig.internal.FontPreferences;
import org.polyfrost.oneconfig.internal.ui.compose.FontGeometrySourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.rdh.argentum.impl.render.gui.TextBatcher$GeometryKey", remap = false)
public abstract class Mixin_ArgentumFontGeometrySource implements FontGeometrySourceKey {
    @Unique private int arcadium$source;
    @Override public int arcadium$fontSource() { return arcadium$source; }

    @Inject(method = "set", at = @At("RETURN"))
    private void arcadium$sourceKey(CallbackInfoReturnable<?> callback) { arcadium$source = FontPreferences.nativeSource().ordinal(); }
    @ModifyReturnValue(method = "equals", at = @At("RETURN"))
    private boolean arcadium$sourceEquals(boolean original, @Local(argsOnly = true) Object other) {
        return original && (!(other instanceof FontGeometrySourceKey key) || key.arcadium$fontSource() == arcadium$source);
    }
    @ModifyReturnValue(method = "hashCode", at = @At("RETURN"))
    private int arcadium$sourceHash(int original) { return 31 * original + arcadium$source; }
}
*///?}
