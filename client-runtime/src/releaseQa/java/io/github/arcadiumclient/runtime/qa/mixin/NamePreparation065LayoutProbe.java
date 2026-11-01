// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.arcadiumclient.runtime.client.branding.NametagLayout;
import io.github.arcadiumclient.runtime.qa.NamePreparation065Qa;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = NametagLayout.class, remap = false)
public abstract class NamePreparation065LayoutProbe {
    @ModifyReturnValue(method = "display", at = @At("RETURN"))
    private static String names$display(String result, String input) { NamePreparation065Qa.text("display", result); return result; }
    @ModifyReturnValue(method = "badge", at = @At("RETURN"))
    private static boolean names$badge(boolean result, String input) { NamePreparation065Qa.text("badge", Boolean.toString(result)); return result; }
}
