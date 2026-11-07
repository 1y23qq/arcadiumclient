// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import net.minecraft.client.render.TextRenderer;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import io.github.arcadiumclient.runtime.client.features.NickHider;
import io.github.arcadiumclient.runtime.client.features.NickHiderConfig;
import io.github.arcadiumclient.runtime.client.features.NickTextProjection;
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
        if (!NickHiderConfig.getEnabled()) return original.call(text, x, y, color);
        boolean outer = NickHider.beginRender();
        try { return original.call(outer ? arcadium$display(text) : text, x, y, color); }
        finally { NickHider.endRender(); }
    }

    @WrapMethod(method = "draw(Ljava/lang/String;III)I")
    private int arcadium$draw(String text, int x, int y, int color, Operation<Integer> original) {
        if (!NickHiderConfig.getEnabled()) return original.call(text, x, y, color);
        boolean outer = NickHider.beginRender();
        try { return original.call(outer ? arcadium$display(text) : text, x, y, color); }
        finally { NickHider.endRender(); }
    }

    @WrapMethod(method = "draw(Ljava/lang/String;FFIZ)I")
    private int arcadium$layer(String text, float x, float y, int color, boolean shadow, Operation<Integer> original) {
        if (!NickHiderConfig.getEnabled()) return original.call(text, x, y, color, shadow);
        boolean outer = NickHider.beginRender();
        try { return original.call(outer ? arcadium$display(text) : text, x, y, color, shadow); }
        finally { NickHider.endRender(); }
    }

    @WrapMethod(method = "getWidth(Ljava/lang/String;)I")
    private int arcadium$width(String text, Operation<Integer> original) {
        if (!NickHiderConfig.getEnabled()) return original.call(text);
        boolean outer = NickHider.beginRender();
        try { return original.call(outer ? arcadium$display(text) : text); }
        finally { NickHider.endRender(); }
    }

    @WrapMethod(method = "trim(Ljava/lang/String;I)Ljava/lang/String;")
    private String arcadium$trim(String text, int width, Operation<String> original) {
        if (!NickHiderConfig.getEnabled()) return original.call(text, width);
        boolean outer = NickHider.beginRender();
        try {
            if (!outer || text == null) return original.call(text, width);
            NickTextProjection projection = NickHider.trimProjection(text);
            return projection.sourceTrim(original.call(projection.getDisplay(), width), false);
        }
        finally { NickHider.endRender(); }
    }

    @WrapMethod(method = "trim(Ljava/lang/String;IZ)Ljava/lang/String;")
    private String arcadium$trimDirection(String text, int width, boolean reverse, Operation<String> original) {
        if (!NickHiderConfig.getEnabled()) return original.call(text, width, reverse);
        boolean outer = NickHider.beginRender();
        try {
            if (!outer || text == null) return original.call(text, width, reverse);
            NickTextProjection projection = NickHider.trimProjection(text);
            return projection.sourceTrim(original.call(projection.getDisplay(), width, reverse), reverse);
        }
        finally { NickHider.endRender(); }
    }

    @WrapMethod(method = "splitAndDraw(Ljava/lang/String;IIII)V")
    private void arcadium$splitDraw(String text, int x, int y, int width, int color, Operation<Void> original) {
        if (!NickHiderConfig.getEnabled()) { original.call(text, x, y, width, color); return; }
        boolean outer = NickHider.beginRender();
        try { original.call(outer ? arcadium$display(text) : text, x, y, width, color); }
        finally { NickHider.endRender(); }
    }

    @WrapMethod(method = "splitAndGetHeight(Ljava/lang/String;I)I")
    private int arcadium$splitHeight(String text, int width, Operation<Integer> original) {
        if (!NickHiderConfig.getEnabled()) return original.call(text, width);
        boolean outer = NickHider.beginRender();
        try { return original.call(outer ? arcadium$display(text) : text, width); }
        finally { NickHider.endRender(); }
    }

    @WrapMethod(method = "split(Ljava/lang/String;I)Ljava/util/List;")
    private List<String> arcadium$split(String text, int width, Operation<List<String>> original) {
        if (!NickHiderConfig.getEnabled()) return original.call(text, width);
        boolean outer = NickHider.beginRender();
        try { return original.call(outer ? arcadium$display(text) : text, width); }
        finally { NickHider.endRender(); }
    }
}
