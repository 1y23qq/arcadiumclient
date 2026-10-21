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

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import org.polyfrost.overflowanimations.legacy.tint.NativeGlint1710;
import org.lwjgl.opengl.GL11;
import org.polyfrost.overflowanimations.config.OverflowAnimationsConfig;
import org.polyfrost.overflowanimations.legacy.entity.LegacyItemRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderItem.class, remap = false)
public abstract class MixinDroppedItems1710 extends Render {
    @Shadow public static boolean field_82407_g;
    @Shadow private static ResourceLocation field_110798_h;

    @ModifyExpressionValue(method = "renderDroppedItem(Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/util/IIcon;IFFFFI)V",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;field_74347_j:Z"))
    private boolean overflowanimations$flatItems(boolean original) {
        var settings = OverflowAnimationsConfig.instance().items;
        return original && !(field_82407_g ? settings.itemFramed2D : settings.itemDrops2D);
    }

    @ModifyArg(method = "renderDroppedItem(Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/util/IIcon;IFFFFI)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V", ordinal = 1), index = 0)
    private float overflowanimations$faceCamera(float original) {
        return !field_82407_g && OverflowAnimationsConfig.instance().items.itemDropsFaceCamera ? 180 - field_76990_c.field_78735_i : original;
    }

    @Inject(method = "renderDroppedItem(Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/util/IIcon;IFFFFI)V", at = {
        @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V", ordinal = 1, shift = At.Shift.AFTER),
        @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V", ordinal = 4, shift = At.Shift.AFTER)
    })
    private void overflowanimations$faceCameraPitch(CallbackInfo ci) {
        var settings = OverflowAnimationsConfig.instance().items;
        if (!field_82407_g && settings.itemDropsFaceCamera && settings.itemDropsFaceCameraRotationFix) GL11.glRotatef(-field_76990_c.field_78732_j, 1, 0, 0);
    }

    @Inject(method = "func_76986_a(Lnet/minecraft/entity/item/EntityItem;DDDFF)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTranslatef(FFF)V", ordinal = 0, shift = At.Shift.AFTER))
    private void overflowanimations$droppedTransform(CallbackInfo ci) {
        if (field_82407_g) return;
        var settings = OverflowAnimationsConfig.instance().items;
        LegacyItemRendering.applyTransform(settings.droppedPositionX, settings.droppedPositionY, settings.droppedPositionZ,
            settings.droppedRotationX, settings.droppedRotationY, settings.droppedRotationZ, settings.droppedScale);
    }
    @Inject(method = "renderDroppedItem(Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/util/IIcon;IFFFFI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;func_78381_a()I", shift = At.Shift.AFTER))
    private void overflowanimations$flatGlint(EntityItem entity, IIcon sprite, int copies, float delta, float red, float green, float blue, int renderPass, CallbackInfo ci) {
        var settings = OverflowAnimationsConfig.instance().items;
        if ((field_82407_g ? settings.glintOnItemFramed2D : settings.glintOnItemDrops2D) && entity.func_92059_d().func_77962_s()) NativeGlint1710.flat(field_110798_h);
    }

}

