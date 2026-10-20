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
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.text.Formatting;
import org.lwjgl.opengl.GL11;
import org.polyfrost.overflowanimations.config.OverflowAnimationsConfig;
import org.polyfrost.overflowanimations.config.category.MovementConfigCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinDinnerbone1710 {
    @Inject(method = "applyRotation", at = @At("TAIL"))
    private void overflowanimations$dinnerbone(LivingEntity entity, float bob, float yaw, float delta, CallbackInfo ci) {
        MovementConfigCategory settings = OverflowAnimationsConfig.instance().movement;
        boolean player = entity instanceof PlayerEntity;
        if (entity.deathTicks > 0 || !(settings.dinnerboneModeEntities || settings.dinnerboneMode && player)) return;
        String name = Formatting.strip(entity.getName());
        if (("Dinnerbone".equals(name) || "Grumm".equals(name)) && (!player || !((PlayerEntity) entity).hidesCape())) return;
        GL11.glTranslatef(0, entity.height + 0.1F, 0);
        GL11.glRotatef(180, 0, 0, 1);
    }
}

//?}
