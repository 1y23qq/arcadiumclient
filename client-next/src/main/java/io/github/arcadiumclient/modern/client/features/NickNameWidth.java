// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.features;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

public final class NickNameWidth {
    private record Access(Object handler, Method instance, Field replacement, Method pattern) {
        private static Access create() {
            try {
                var type = Class.forName("dev.microcontrollers.simplenickhider.config.SimpleNickHiderConfig");
                return new Access(type.getField("CONFIG").get(null),
                    Class.forName("dev.isxander.yacl3.config.v2.api.ConfigClassHandler").getMethod("instance"),
                    type.getField("replacement"), Class.forName("dev.microcontrollers.simplenickhider.SimpleNickHider").getMethod("getUsernamePattern"));
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Simple Nick Hider layout API is unavailable", failure); }
        }
    }
    private static final class Lazy { private static final Access API = Access.create(); }
    private NickNameWidth() {}
    public static FormattedText measured(FormattedText original) {
        if (!NickHiderConfig.getEnabled()) return original;
        try {
            var api = Lazy.API;
            String replacement = (String) api.replacement.get(api.instance.invoke(api.handler));
            if (replacement == null || replacement.isEmpty()) return original;
            var pattern = (Pattern) api.pattern.invoke(null);
            var fragments = new ArrayList<FormattedText>();
            original.visit((style, text) -> {
                fragments.add(FormattedText.of(pattern.matcher(text).replaceAll(Matcher.quoteReplacement(replacement)), style));
                return Optional.empty();
            }, Style.EMPTY);
            return FormattedText.composite(fragments);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Could not measure the active nickname", failure); }
    }
}
