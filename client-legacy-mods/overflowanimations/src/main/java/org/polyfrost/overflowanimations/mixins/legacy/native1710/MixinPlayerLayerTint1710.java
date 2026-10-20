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
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.ItemInHandRenderer;
import net.minecraft.client.render.entity.PlayerRenderer;
import net.minecraft.client.render.model.entity.HumanoidModel;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.item.ItemStack;
import org.polyfrost.overflowanimations.legacy.tint.LegacyDamageTint;
import org.polyfrost.overflowanimations.legacy.tint.NativeDamageTint1710;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerRenderer.class)
public abstract class MixinPlayerLayerTint1710 {
    @WrapOperation(method = "renderMore(Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/model/entity/HumanoidModel;renderCape(F)V"))
    private void overflowanimations$capeTint(HumanoidModel model, float scale, Operation<Void> original, @Local(argsOnly = true) ClientPlayerEntity entity) {
        original.call(model, scale);
        if (LegacyDamageTint.config().damageTintCape && NativeDamageTint1710.begin(entity)) {
            try { original.call(model, scale); }
            finally { NativeDamageTint1710.end(); }
        }
    }

    @WrapOperation(method = "renderMore(Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/ItemInHandRenderer;render(Lnet/minecraft/entity/living/LivingEntity;Lnet/minecraft/item/ItemStack;I)V"))
    private void overflowanimations$itemTint(ItemInHandRenderer renderer, LivingEntity entity, ItemStack item, int pass, Operation<Void> original) {
        original.call(renderer, entity, item, pass);
        if (LegacyDamageTint.config().damageTintItems && NativeDamageTint1710.begin(entity)) {
            try { original.call(renderer, entity, item, pass); }
            finally { NativeDamageTint1710.end(); }
        }
    }
}

//?}
