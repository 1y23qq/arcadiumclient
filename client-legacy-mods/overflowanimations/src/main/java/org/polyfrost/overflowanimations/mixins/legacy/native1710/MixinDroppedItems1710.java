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
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.entity.ItemEntity;
import net.minecraft.client.render.texture.Sprite;
import net.minecraft.resource.Identifier;
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

@Mixin(ItemRenderer.class)
public abstract class MixinDroppedItems1710 extends EntityRenderer {
    @Shadow public static boolean forceRotation;
    @Shadow private static Identifier ENCHANTMENT_GLINT_LOCATION;

    @ModifyExpressionValue(method = "render(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/texture/Sprite;IFFFF)V",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/options/GameOptions;fancyGraphics:Z"))
    private boolean overflowanimations$flatItems(boolean original) {
        var settings = OverflowAnimationsConfig.instance().items;
        return original && !(forceRotation ? settings.itemFramed2D : settings.itemDrops2D);
    }

    @ModifyArg(method = "render(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/texture/Sprite;IFFFF)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V", ordinal = 1), index = 0)
    private float overflowanimations$faceCamera(float original) {
        return !forceRotation && OverflowAnimationsConfig.instance().items.itemDropsFaceCamera ? 180 - dispatcher.cameraYaw : original;
    }

    @Inject(method = "render(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/texture/Sprite;IFFFF)V", at = {
        @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V", ordinal = 1, shift = At.Shift.AFTER),
        @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V", ordinal = 4, shift = At.Shift.AFTER)
    })
    private void overflowanimations$faceCameraPitch(CallbackInfo ci) {
        var settings = OverflowAnimationsConfig.instance().items;
        if (!forceRotation && settings.itemDropsFaceCamera && settings.itemDropsFaceCameraRotationFix) GL11.glRotatef(-dispatcher.cameraPitch, 1, 0, 0);
    }

    @Inject(method = "render(Lnet/minecraft/entity/ItemEntity;DDDFF)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTranslatef(FFF)V", ordinal = 0, shift = At.Shift.AFTER))
    private void overflowanimations$droppedTransform(CallbackInfo ci) {
        if (forceRotation) return;
        var settings = OverflowAnimationsConfig.instance().items;
        LegacyItemRendering.applyTransform(settings.droppedPositionX, settings.droppedPositionY, settings.droppedPositionZ,
            settings.droppedRotationX, settings.droppedRotationY, settings.droppedRotationZ, settings.droppedScale);
    }
    @Inject(method = "render(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/texture/Sprite;IFFFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/LegacyTesselatorBackend;end()I", shift = At.Shift.AFTER))
    private void overflowanimations$flatGlint(ItemEntity entity, Sprite sprite, int copies, float delta, float red, float green, float blue, CallbackInfo ci) {
        var settings = OverflowAnimationsConfig.instance().items;
        if ((forceRotation ? settings.glintOnItemFramed2D : settings.glintOnItemDrops2D) && entity.getItem().hasEnchantmentGlint()) NativeGlint1710.flat(ENCHANTMENT_GLINT_LOCATION);
    }

}

//?}
