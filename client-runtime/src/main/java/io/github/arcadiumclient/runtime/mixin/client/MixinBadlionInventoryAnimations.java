// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.screen.game.inventory.SurvivalInventoryScreen;
import net.minecraft.entity.living.LivingEntity;
import io.github.arcadiumclient.runtime.client.features.BadlionAnimationContext;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SurvivalInventoryScreen.class)
public abstract class MixinBadlionInventoryAnimations {
    @WrapMethod(method = "renderEntity")
    private static void arcadium$inventoryContext(int x, int y, int scale, float mouseX, float mouseY, LivingEntity entity, Operation<Void> original) {
        BadlionAnimationContext.enterInventory();
        try { original.call(x, y, scale, mouseX, mouseY, entity); }
        finally { BadlionAnimationContext.exitInventory(); }
    }
}
