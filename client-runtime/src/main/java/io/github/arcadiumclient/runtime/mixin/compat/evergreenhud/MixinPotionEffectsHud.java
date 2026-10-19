// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.compat.evergreenhud;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import net.minecraft.entity.living.effect.StatusEffect;
import org.polyfrost.compose.render.PolyColor;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@IfModLoaded("evergreenhud")
@Mixin(targets = "org.polyfrost.evergreenhud.client.hud.potion.PotionEffectsHud", remap = false)
public abstract class MixinPotionEffectsHud {
    @Unique
    @Switch(title = "Potion Colors", description = "Use each effect's vanilla potion color for its name and duration.", subcategory = "Colors")
    private boolean potionColors = true;

    @WrapOperation(method = "row*", at = {
            @At(value = "INVOKE", target = "Lorg/polyfrost/evergreenhud/client/hud/potion/EffectComponentValues;getNameColor()Lorg/polyfrost/compose/render/PolyColor;"),
            @At(value = "INVOKE", target = "Lorg/polyfrost/evergreenhud/client/hud/potion/EffectComponentValues;getDurationColor()Lorg/polyfrost/compose/render/PolyColor;")
    })
    private PolyColor arcadium$potionColor(@Coerce Object settings, Operation<PolyColor> original,
                                         @Local(argsOnly = true) StatusEffect effect) {
        PolyColor configured = original.call(settings);
        if (!potionColors || effect == null) return configured;
        return new PolyColor((configured.getArgb() & 0xFF000000) | effect.getPotionColor());
    }
}
