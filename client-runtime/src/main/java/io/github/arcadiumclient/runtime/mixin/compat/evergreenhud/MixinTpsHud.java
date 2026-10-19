// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.compat.evergreenhud;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import org.polyfrost.compose.render.PolyColor;
import org.polyfrost.oneconfig.api.config.v1.Property;
import org.polyfrost.oneconfig.api.config.v1.annotations.Color;
import org.polyfrost.oneconfig.api.hud.v1.Hud;
import io.github.arcadiumclient.runtime.client.features.HudValuePalette;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@IfModLoaded("evergreenhud")
@Mixin(targets = "org.polyfrost.evergreenhud.client.hud.TpsHud", remap = false)
public abstract class MixinTpsHud {
    @Shadow private boolean colorByValue;
    @Unique private float arcadium$currentTps;

    @Unique @Color(title = "19.5 to 20.5 TPS", subcategory = "Colors", alpha = false)
    private int tpsHealthy = HudValuePalette.PING_BEST;
    @Unique @Color(title = "15.1 to 19.4 TPS", subcategory = "Colors", alpha = false)
    private int tpsSlow = HudValuePalette.PING_FAIR;
    @Unique @Color(title = "15.0 or below, 20.6 or above", subcategory = "Colors", alpha = false)
    private int tpsOutsideRange = HudValuePalette.PING_POOR;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void arcadium$defaultColors(CallbackInfo ci) {
        colorByValue = true;
    }

    @Inject(method = "setup", at = @At("RETURN"))
    private void arcadium$describeColors(CallbackInfo ci) {
        Hud hud = (Hud) (Object) this;
        if (!hud.isReal()) return;
        var tree = hud.getTree();
        var option = tree.getProp("colorByValue");
        if (option != null) option.addMetadata("description", "Colors are selected from the displayed TPS rounded to one decimal place.");
        for (String id : new String[]{"accuracy", "trailingZeros"}) {
            var formatting = tree.getProp(id);
            if (formatting != null) formatting.addDisplayCondition(() -> Property.Display.HIDDEN);
        }
    }

    @Redirect(method = "record", at = @At(value = "INVOKE", target = "Lkotlin/ranges/RangesKt;coerceIn(FFF)F"))
    private float arcadium$keepHighTps(float value, float minimum, float maximum) {
        arcadium$currentTps = Math.max(minimum, value);
        return arcadium$currentTps;
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lorg/polyfrost/evergreenhud/client/hud/TpsHud;format(Ljava/lang/Number;)Ljava/lang/String;"))
    private String arcadium$oneDecimal(@Coerce Object hud, Number value, Operation<String> original) {
        return HudValuePalette.tpsText(value.floatValue());
    }

    @Inject(method = "valueColor", at = @At("RETURN"), cancellable = true)
    private void arcadium$tpsColor(CallbackInfoReturnable<PolyColor> cir) {
        if (!colorByValue || cir.getReturnValue() == null) return;
        int color = switch (HudValuePalette.tpsRange(arcadium$currentTps)) {
            case 0 -> tpsHealthy;
            case 1 -> tpsSlow;
            default -> tpsOutsideRange;
        };
        cir.setReturnValue(new PolyColor(color | 0xFF000000));
    }
}
