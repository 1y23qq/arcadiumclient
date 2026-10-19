// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import net.minecraft.client.render.TextRenderUtils;
import net.minecraft.text.Text;
import io.github.arcadiumclient.runtime.client.features.LegacyNickHiderText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(TextRenderUtils.class)
public abstract class MixinNickHiderTextWrapping {
    @ModifyVariable(method = "wrapText", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static Text arcadium$maskWrappedText(Text text) {
        return LegacyNickHiderText.display(text);
    }
}
