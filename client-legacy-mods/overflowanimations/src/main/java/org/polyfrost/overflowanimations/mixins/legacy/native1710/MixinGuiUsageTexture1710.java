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
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.client.render.texture.Sprite;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.item.ItemStack;
import org.polyfrost.overflowanimations.legacy.entity.LegacyItemRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemRenderer.class)
public abstract class MixinGuiUsageTexture1710 {
    @ModifyExpressionValue(method = "renderGuiItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;getSprite()Lnet/minecraft/client/render/texture/Sprite;"))
    private Sprite overflowanimations$usingTexture(Sprite original, @Local(argsOnly = true) ItemStack item) {
        String model = LegacyItemRendering.guiUseModel(item);
        if (model == null) return original;
        var texture = Minecraft.getInstance().getTextureManager().get(TextureAtlas.ITEMS_LOCATION);
        return texture instanceof TextureAtlas atlas ? atlas.getSprite("minecraft:" + model) : original;
    }
}

//?}
