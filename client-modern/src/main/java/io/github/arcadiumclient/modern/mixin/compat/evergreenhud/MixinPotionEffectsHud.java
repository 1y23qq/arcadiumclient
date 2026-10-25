// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin.compat.evergreenhud;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
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
    @Unique @Switch(title = "Potion Colors", subcategory = "Colors") private boolean potionColors = true;
    @WrapOperation(method = "row*", at = {
        @At(value = "INVOKE", target = "Lorg/polyfrost/evergreenhud/client/hud/potion/EffectComponentValues;getNameColor()Lorg/polyfrost/compose/render/PolyColor;"),
        @At(value = "INVOKE", target = "Lorg/polyfrost/evergreenhud/client/hud/potion/EffectComponentValues;getDurationColor()Lorg/polyfrost/compose/render/PolyColor;")
    })
    private PolyColor arcadium$color(@Coerce Object settings, Operation<PolyColor> original, @Local(argsOnly = true) Identifier id) {
        PolyColor configured = original.call(settings);
        var effect = BuiltInRegistries.MOB_EFFECT.getValue(id);
        return !potionColors || effect == null ? configured : new PolyColor(configured.getArgb() & 0xFF000000 | effect.getColor());
    }
}
