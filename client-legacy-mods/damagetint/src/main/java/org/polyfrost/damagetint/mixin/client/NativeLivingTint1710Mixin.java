//? if =1.7.10 {
package org.polyfrost.damagetint.mixin.client;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.polyfrost.damagetint.client.utils.NativeTint1710;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(LivingEntityRenderer.class)
public abstract class NativeLivingTint1710Mixin {
    @ModifyArgs(method = "render(Lnet/minecraft/world/entity/LivingEntity;DDDFF)V", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL11;glColor4f(FFFF)V", ordinal = 3, remap = false))
    private void damagetint$body(Args args, LivingEntity entity, double x, double y, double z, float yaw, float tickDelta) {
        NativeTint1710.apply(args, entity);
    }

    @ModifyArgs(method = "render(Lnet/minecraft/world/entity/LivingEntity;DDDFF)V", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL11;glColor4f(FFFF)V", ordinal = 4, remap = false))
    private void damagetint$armor(Args args, LivingEntity entity, double x, double y, double z, float yaw, float tickDelta) {
        NativeTint1710.apply(args, entity);
    }
}
//?}
