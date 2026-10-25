// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.mixin.compat.evergreenhud;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import org.polyfrost.compose.render.PolyColor;
import org.polyfrost.oneconfig.api.config.v1.annotations.Color;
import org.polyfrost.oneconfig.api.hud.v1.Hud;
import io.github.arcadiumclient.modern.client.features.HudValuePalette;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@IfModLoaded("evergreenhud")
@Mixin(targets = "org.polyfrost.evergreenhud.client.hud.PingHud", remap = false)
public abstract class MixinPingHud {
    @Shadow private Long lastPing;
    @Shadow private boolean colorByValue;

    @Unique @Color(title = "0 to 74 ms", subcategory = "Colors", alpha = false)
    private int ping0To74 = HudValuePalette.PING_BEST;
    @Unique @Color(title = "75 to 144 ms", subcategory = "Colors", alpha = false)
    private int ping75To144 = HudValuePalette.PING_GOOD;
    @Unique @Color(title = "145 to 199 ms", subcategory = "Colors", alpha = false)
    private int ping145To199 = HudValuePalette.PING_FAIR;
    @Unique @Color(title = "200 to 299 ms", subcategory = "Colors", alpha = false)
    private int ping200To299 = HudValuePalette.PING_HIGH;
    @Unique @Color(title = "300 to 399 ms", subcategory = "Colors", alpha = false)
    private int ping300To399 = HudValuePalette.PING_POOR;
    @Unique @Color(title = "400 ms and above", subcategory = "Colors", alpha = false)
    private int ping400AndAbove = HudValuePalette.PING_WORST;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void arcadium$defaultColors(CallbackInfo ci) {
        colorByValue = true;
    }

    @Inject(method = "setup", at = @At("RETURN"))
    private void arcadium$describeColors(CallbackInfo ci) {
        Hud hud = (Hud) (Object) this;
        if (!hud.isReal()) return;
        var option = hud.getTree().getProp("colorByValue");
        if (option != null) option.addMetadata("description", "Use the same ping ranges and default palette as the Tab list.");
    }

    @Inject(method = "shouldShow", at = @At("RETURN"), cancellable = true)
    private void arcadium$keepZeroPing(CallbackInfoReturnable<Boolean> cir) {
        if (lastPing != null && lastPing == 0L) cir.setReturnValue(true);
    }

    @Inject(method = "valueColor", at = @At("HEAD"), cancellable = true)
    private void arcadium$pingColor(CallbackInfoReturnable<PolyColor> cir) {
        if (!colorByValue || lastPing == null) {
            cir.setReturnValue(null);
            return;
        }
        int range = HudValuePalette.pingRange(lastPing);
        if (range < 0) {
            cir.setReturnValue(null);
            return;
        }
        int color = switch (range) {
            case 0 -> ping0To74;
            case 1 -> ping75To144;
            case 2 -> ping145To199;
            case 3 -> ping200To299;
            case 4 -> ping300To399;
            case 5 -> ping400AndAbove;
            default -> 0;
        };
        cir.setReturnValue(new PolyColor(color | 0xFF000000));
    }
}
