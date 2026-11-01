// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.NamePreparation065Qa;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = {Entity.class, PlayerEntity.class}, priority = 3000)
public abstract class NamePreparation065DisplayProbe {
    @WrapMethod(method = "getDisplayName")
    private Text names$assembly(Operation<Text> original) { NamePreparation065Qa.assembly(); return original.call(); }
}
