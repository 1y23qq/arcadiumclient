/**
 * OverflowAnimations
 * The all-you-could-want legacy animations mod for modern minecraft versions.
 * Brings back animations from the 1.7/1.8 era and more.
 * <p>
 * Copyright (C) 2024-2027 lowercasebtw
 * Copyright (C) 2024-2027 mixces
 * Copyright (C) 2024-2027 Contributors to the project retain their copyright
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * "MINECRAFT" LINKING EXCEPTION TO THE GPL
 */

package org.polyfrost.overflowanimations.mixins.legacy.native1710;

//? if =1.7.10 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import net.minecraft.client.render.ItemInHandRenderer;
import net.minecraft.client.render.vertex.LegacyTesselatorBackend;
import org.polyfrost.overflowanimations.config.OverflowAnimationsConfig;
import org.polyfrost.overflowanimations.legacy.tint.NativeGlint1710;
import org.polyfrost.overflowanimations.util.enums.ItemGlintSetting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(ItemInHandRenderer.class)
public abstract class MixinHandGlint1710 {
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/ItemInHandRenderer;draw(Lnet/minecraft/client/render/vertex/LegacyTesselatorBackend;FFFFIIF)V"),
        slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/client/render/ItemInHandRenderer;ENCHANTMENT_GLINT_LOCATION:Lnet/minecraft/resource/Identifier;")))
    private void overflowanimations$modernHandGlint(LegacyTesselatorBackend vertices, float u1, float v1, float u2, float v2,
        int width, int height, float thickness, Operation<Void> original, @Share("glintLayer") LocalIntRef layer) {
        if (OverflowAnimationsConfig.instance().items.itemGlint != ItemGlintSetting.V1_15) {
            original.call(vertices, u1, v1, u2, v2, width, height, thickness);
        } else if (layer.get() == 0) {
            NativeGlint1710.hand(vertices, thickness);
        }
        layer.set(layer.get() + 1);
    }
}

//?}
