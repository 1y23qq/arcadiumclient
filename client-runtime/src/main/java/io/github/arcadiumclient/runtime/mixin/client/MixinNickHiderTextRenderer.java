// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import net.minecraft.client.render.TextRenderer;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import io.github.arcadiumclient.runtime.client.features.NickHider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = TextRenderer.class, priority = 1600)
public abstract class MixinNickHiderTextRenderer {
    @Unique
    private static String arcadium$display(String text) {
        return text == null ? null : NickHider.display(text);
    }

    @WrapMethod(method = "drawWithShadow(Ljava/lang/String;FFI)I")
    private int arcadium$shadow(String text, float x, float y, int color, Operation<Integer> original) {
        return original.call(arcadium$display(text), x, y, color);
    }

    @WrapMethod(method = "draw(Ljava/lang/String;III)I")
    private int arcadium$draw(String text, int x, int y, int color, Operation<Integer> original) {
        return original.call(arcadium$display(text), x, y, color);
    }

    @WrapMethod(method = "draw(Ljava/lang/String;FFIZ)I")
    private int arcadium$layer(String text, float x, float y, int color, boolean shadow, Operation<Integer> original) {
        return original.call(arcadium$display(text), x, y, color, shadow);
    }

    @WrapMethod(method = "getWidth(Ljava/lang/String;)I")
    private int arcadium$width(String text, Operation<Integer> original) {
        return original.call(arcadium$display(text));
    }

    @WrapMethod(method = "trim(Ljava/lang/String;I)Ljava/lang/String;")
    private String arcadium$trim(String text, int width, Operation<String> original) {
        return original.call(arcadium$display(text), width);
    }

    @WrapMethod(method = "trim(Ljava/lang/String;IZ)Ljava/lang/String;")
    private String arcadium$trimDirection(String text, int width, boolean reverse, Operation<String> original) {
        return original.call(arcadium$display(text), width, reverse);
    }

    @WrapMethod(method = "splitAndDraw(Ljava/lang/String;IIII)V")
    private void arcadium$splitDraw(String text, int x, int y, int width, int color, Operation<Void> original) {
        original.call(arcadium$display(text), x, y, width, color);
    }

    @WrapMethod(method = "splitAndGetHeight(Ljava/lang/String;I)I")
    private int arcadium$splitHeight(String text, int width, Operation<Integer> original) {
        return original.call(arcadium$display(text), width);
    }

    @WrapMethod(method = "split(Ljava/lang/String;I)Ljava/util/List;")
    private List<String> arcadium$split(String text, int width, Operation<List<String>> original) {
        return original.call(arcadium$display(text), width);
    }
}
