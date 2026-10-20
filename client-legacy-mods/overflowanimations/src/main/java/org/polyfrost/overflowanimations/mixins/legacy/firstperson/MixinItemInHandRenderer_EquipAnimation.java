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

package org.polyfrost.overflowanimations.mixins.legacy.firstperson;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.ItemInHandRenderer;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.polyfrost.overflowanimations.legacy.firstperson.LegacyFirstPerson;
import org.polyfrost.overflowanimations.util.enums.EquipAnimationVersionSetting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class MixinItemInHandRenderer_EquipAnimation {
    @Shadow private Minecraft minecraft;
    @Shadow private ItemStack itemInHand;
    @Shadow private int selectedSlot;
    @Shadow private float handHeight;
    @Unique private boolean overflowanimations$modernKeep;

    @WrapMethod(method = "tick()V")
    private void overflowanimations$modernComparison(Operation<Void> original) {
        if (LegacyFirstPerson.items().equipAnimationVersion != EquipAnimationVersionSetting.V1_8) {
            original.call();
            return;
        }
        PlayerInventory inventory = minecraft.player.inventory;
        ItemStack previousStack = itemInHand;
        boolean previousKeep = overflowanimations$modernKeep;
        boolean keep = selectedSlot == inventory.selectedSlot && ItemStack.matches(previousStack, inventory.getSelectedItem());
        overflowanimations$modernKeep = keep;
        try {
            original.call();
            if (!keep && handHeight >= 0.1f) itemInHand = previousStack;
        } finally {
            overflowanimations$modernKeep = previousKeep;
        }
    }

    @ModifyExpressionValue(method = "tick", at = @At(value = "CONSTANT", args = "floatValue=0.4F"))
    private float overflowanimations$reequipSpeed(float original) {
        return LegacyFirstPerson.items().reequipSpeed;
    }

    @ModifyVariable(method = "tick()V", at = @At("LOAD"), index = 3)
    private int overflowanimations$equipLogic(int nativeKeep) {
        EquipAnimationVersionSetting setting = LegacyFirstPerson.items().equipAnimationVersion;
        if (setting == EquipAnimationVersionSetting.DISABLED) {
            PlayerInventory inventory = minecraft.player.inventory;
            itemInHand = inventory.getSelectedItem();
            selectedSlot = inventory.selectedSlot;
            return 1;
        }
        return setting == EquipAnimationVersionSetting.V1_8 ? (overflowanimations$modernKeep ? 1 : 0) : nativeKeep;
    }

    @Inject(method = {"onBlockUsed", "onItemUsed"}, at = @At("HEAD"), cancellable = true)
    private void overflowanimations$disableEquipOnUse(CallbackInfo ci) {
        if (LegacyFirstPerson.items().equipAnimationVersion == EquipAnimationVersionSetting.DISABLED) ci.cancel();
    }
}
