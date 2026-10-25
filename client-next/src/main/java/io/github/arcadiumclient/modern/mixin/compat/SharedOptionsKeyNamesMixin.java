// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin.compat;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.Map;
import net.minecraft.util.datafix.fixes.OptionsKeyTranslationFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(OptionsKeyTranslationFix.class)
public abstract class SharedOptionsKeyNamesMixin {
    @Inject(method = "lambda$makeRule$3", at = @At("HEAD"), cancellable = true)
    private static void arcadium$keepKeyboardNamespace(Dynamic<?> options, Map.Entry<Dynamic<?>, Dynamic<?>> entry,
            CallbackInfoReturnable<Pair<Dynamic<?>, Dynamic<?>>> cir) {
        if (entry.getKey().asString("").startsWith("key_") && entry.getValue().asString("").startsWith("key.keyboard."))
            cir.setReturnValue(Pair.of(entry.getKey(), entry.getValue()));
    }
}
