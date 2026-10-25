// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.test;

import com.google.gson.JsonPrimitive;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import io.github.arcadiumclient.modern.mixin.compat.SharedOptionsKeyNamesMixin;
import io.github.arcadiumclient.modern.mixin.compat.SharedOptionsLwjglKeysMixin;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import net.minecraft.util.datafix.fixes.OptionsKeyLwjgl3Fix;
import net.minecraft.util.datafix.fixes.OptionsKeyTranslationFix;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import static org.junit.jupiter.api.Assertions.*;

class SharedOptionsKeyMigrationTest {
    @Test void nativeLegacyConverterReproducesTheReportedFailure() throws Exception {
        var nativeMethod = OptionsKeyLwjgl3Fix.class.getDeclaredMethod("method_5122", Map.Entry.class);
        nativeMethod.setAccessible(true);
        var failure = assertThrows(InvocationTargetException.class,
            () -> nativeMethod.invoke(null, entry("key_key.sprint", "key.keyboard.unknown")));
        assertInstanceOf(NumberFormatException.class, failure.getCause());
    }

    @Test void alreadyNamedKeysSurviveBothRealNativeConverters() throws Exception {
        for (String key : new String[]{"key.keyboard.unknown", "key.keyboard.j", "key.mouse.5", "scancode.42"})
            assertEquals(key, migrate("key_key.jump", key));
    }

    @Test void numericAndIntermediateKeysStillUseTheNativeMigration() throws Exception {
        assertEquals("key.keyboard.w", migrate("key_key.forward", "17"));
        assertEquals("key.keyboard.unknown", migrate("key_key.sprint", "0"));
        assertEquals("key.mouse.left", migrate("key_key.attack", "-100"));
        assertEquals("key.mouse.5", migrate("key_arcadium.custom", "-96"));
        assertEquals("key.keyboard.g", migrate("key_arcadium.custom", "key.g"));
        assertEquals("en_US", migrate("lang", "en_US"));
    }

    private static String migrate(String name, String value) throws Exception {
        var input = entry(name, value);
        var first = new CallbackInfoReturnable<Pair<Dynamic<?>, Dynamic<?>>>("legacyKeys", true);
        var guard = SharedOptionsLwjglKeysMixin.class.getDeclaredMethod("arcadium$keepNamedKeys", Map.Entry.class, CallbackInfoReturnable.class);
        guard.setAccessible(true); guard.invoke(null, input, first);
        Pair<?, ?> result;
        if (first.isCancelled()) result = first.getReturnValue();
        else {
            var original = OptionsKeyLwjgl3Fix.class.getDeclaredMethod("method_5122", Map.Entry.class);
            original.setAccessible(true); result = (Pair<?, ?>) original.invoke(null, input);
        }
        var intermediate = Map.entry((Dynamic<?>) result.getFirst(), (Dynamic<?>) result.getSecond());
        var second = new CallbackInfoReturnable<Pair<Dynamic<?>, Dynamic<?>>>("keyNames", true);
        var names = SharedOptionsKeyNamesMixin.class.getDeclaredMethod("arcadium$keepKeyboardNamespace", Dynamic.class, Map.Entry.class, CallbackInfoReturnable.class);
        names.setAccessible(true); names.invoke(null, input.getKey(), intermediate, second);
        if (second.isCancelled()) result = second.getReturnValue();
        else {
            var original = OptionsKeyTranslationFix.class.getDeclaredMethod("method_28238", Dynamic.class, Map.Entry.class);
            original.setAccessible(true); result = (Pair<?, ?>) original.invoke(null, input.getKey(), intermediate);
        }
        return ((Dynamic<?>) result.getSecond()).asString("");
    }

    private static Map.Entry<Dynamic<?>, Dynamic<?>> entry(String name, String value) {
        return Map.entry(new Dynamic<>(JsonOps.INSTANCE, new JsonPrimitive(name)), new Dynamic<>(JsonOps.INSTANCE, new JsonPrimitive(value)));
    }
}
