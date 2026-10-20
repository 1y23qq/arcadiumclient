//? if =1.7.10 {
package org.polyfrost.damagetint.mixin.client;

import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.polyfrost.damagetint.client.utils.NativeTint1710;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(EnderDragonRenderer.class)
public abstract class NativeDragonTint1710Mixin {
    @ModifyArgs(method = "renderModel(Lnet/minecraft/world/entity/boss/enderdragon/EnderDragon;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glColor4f(FFFF)V", ordinal = 0, remap = false))
    private void damagetint$dragon(Args args, EnderDragon entity, float limbAngle, float limbDistance,
                                   float age, float yaw, float pitch, float scale) {
        NativeTint1710.apply(args, entity);
    }
}
//?}
