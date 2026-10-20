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
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.model.Model;
import net.minecraft.entity.Entity;
import net.minecraft.resource.Identifier;
import org.lwjgl.opengl.GL11;
import org.polyfrost.overflowanimations.config.OverflowAnimationsConfig;
import org.polyfrost.overflowanimations.legacy.tint.LegacyGlint;
import org.polyfrost.overflowanimations.util.enums.ArmorGlintSetting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinArmorGlint1710 {
    @ModifyExpressionValue(method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;ENCHANTMENT_GLINT_LOCATION:Lnet/minecraft/resource/Identifier;"))
    private Identifier overflowanimations$armorTexture(Identifier original) {
        if (!LegacyGlint.replacesArmorGlint()) return original;
        return OverflowAnimationsConfig.instance().items.armorGlint == ArmorGlintSetting.V1_19_4 ? LegacyGlint.MODERN_ARMOR_TEXTURE : LegacyGlint.MODERN_ITEM_TEXTURE;
    }

    @WrapOperation(method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/model/Model;render(Lnet/minecraft/entity/Entity;FFFFFF)V"),
        slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;ENCHANTMENT_GLINT_LOCATION:Lnet/minecraft/resource/Identifier;"),
                       to = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;renderMore(Lnet/minecraft/entity/living/LivingEntity;F)V")))
    private void overflowanimations$modernArmorGlint(Model model, Entity entity, float walk, float speed, float bob, float yaw,
        float pitch, float scale, Operation<Void> original, @Local(index = 21) int layer) {
        if (!LegacyGlint.replacesArmorGlint()) {
            original.call(model, entity, walk, speed, bob, yaw, pitch, scale);
            return;
        }
        if (layer != 0) return;
        boolean modern = OverflowAnimationsConfig.instance().items.armorGlint == ArmorGlintSetting.V1_19_4;
        float strength = modern ? LegacyGlint.modernStrength() : 1;
        GL11.glColor4f(strength, strength, strength, 1);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();
        try {
            GL11.glLoadIdentity();
            LegacyGlint.setupModernTexturing(0.16F, modern ? LegacyGlint.modernSpeed() : 1);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            original.call(model, entity, walk, speed, bob, yaw, pitch, scale);
        } finally {
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
        }
    }
}

//?}
