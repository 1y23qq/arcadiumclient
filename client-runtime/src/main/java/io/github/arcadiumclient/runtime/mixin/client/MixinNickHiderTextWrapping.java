// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import net.minecraft.client.render.TextRenderUtils;
import net.minecraft.text.Text;
import io.github.arcadiumclient.runtime.client.features.LegacyNickHiderText;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.client.render.TextRenderer;
import java.util.List;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.client.features.NickHider;
import io.github.arcadiumclient.runtime.client.features.NickHiderConfig;

@Mixin(TextRenderUtils.class)
public abstract class MixinNickHiderTextWrapping {
    @WrapMethod(method = "wrapText")
    private static List<Text> arcadium$maskWrappedText(Text text, int width, TextRenderer renderer, boolean preserveWords, boolean forceColors, Operation<List<Text>> original) {
        if (!NickHiderConfig.getEnabled()) return original.call(text, width, renderer, preserveWords, forceColors);
        NickHider.beginRender();
        try { return LegacyNickHiderText.renderedLines(original.call(LegacyNickHiderText.display(text), width, renderer, preserveWords, forceColors)); }
        finally { NickHider.endRender(); }
    }
}
