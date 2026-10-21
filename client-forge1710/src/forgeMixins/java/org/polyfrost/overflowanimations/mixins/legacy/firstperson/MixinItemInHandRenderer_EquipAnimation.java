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
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.entity.player.InventoryPlayer;
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

@Mixin(value = ItemRenderer.class, remap = false)
public abstract class MixinItemInHandRenderer_EquipAnimation {
    @Shadow private Minecraft field_78455_a;
    @Shadow private ItemStack field_78453_b;
    @Shadow private int field_78450_g;
    @Shadow private float field_78454_c;
    @Unique private boolean overflowanimations$modernKeep;

    @WrapMethod(method = "func_78441_a()V")
    private void overflowanimations$modernComparison(Operation<Void> original) {
        if (LegacyFirstPerson.items().equipAnimationVersion != EquipAnimationVersionSetting.V1_8) {
            original.call();
            return;
        }
        InventoryPlayer inventory = field_78455_a.field_71439_g.field_71071_by;
        ItemStack previousStack = field_78453_b;
        boolean previousKeep = overflowanimations$modernKeep;
        boolean keep = field_78450_g == inventory.field_70461_c && ItemStack.func_77989_b(previousStack, inventory.func_70448_g());
        overflowanimations$modernKeep = keep;
        try {
            original.call();
            if (!keep && field_78454_c >= 0.1f) field_78453_b = previousStack;
        } finally {
            overflowanimations$modernKeep = previousKeep;
        }
    }

    @ModifyExpressionValue(method = "func_78441_a", at = @At(value = "CONSTANT", args = "floatValue=0.4F"))
    private float overflowanimations$reequipSpeed(float original) {
        return LegacyFirstPerson.items().reequipSpeed;
    }

    @ModifyVariable(method = "func_78441_a()V", at = @At("LOAD"), index = 3)
    private boolean overflowanimations$equipLogic(boolean nativeKeep) {
        EquipAnimationVersionSetting setting = LegacyFirstPerson.items().equipAnimationVersion;
        if (setting == EquipAnimationVersionSetting.DISABLED) {
            InventoryPlayer inventory = field_78455_a.field_71439_g.field_71071_by;
            field_78453_b = inventory.func_70448_g();
            field_78450_g = inventory.field_70461_c;
            return true;
        }
        return setting == EquipAnimationVersionSetting.V1_8 ? overflowanimations$modernKeep : nativeKeep;
    }

    @Inject(method = {"func_78444_b", "func_78445_c"}, at = @At("HEAD"), cancellable = true)
    private void overflowanimations$disableEquipOnUse(CallbackInfo ci) {
        if (LegacyFirstPerson.items().equipAnimationVersion == EquipAnimationVersionSetting.DISABLED) ci.cancel();
    }
}
