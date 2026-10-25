// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin.compat;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.Map;
import net.minecraft.util.datafix.fixes.OptionsKeyLwjgl3Fix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(OptionsKeyLwjgl3Fix.class)
public abstract class SharedOptionsLwjglKeysMixin {
    @Inject(method = "lambda$makeRule$3", at = @At("HEAD"), cancellable = true)
    private static void arcadium$keepNamedKeys(Map.Entry<Dynamic<?>, Dynamic<?>> entry,
            CallbackInfoReturnable<Pair<Dynamic<?>, Dynamic<?>>> cir) {
        // Shared options can retain named keys under an older data version.
        String value = entry.getValue().asString("");
        if (entry.getKey().asString("").startsWith("key_") && (value.startsWith("key.") || value.startsWith("scancode.")))
            cir.setReturnValue(Pair.of(entry.getKey(), entry.getValue()));
    }
}
