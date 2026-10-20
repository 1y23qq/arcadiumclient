//? if =1.7.10 {
package dev.microcontrollers.mountopacity.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.microcontrollers.mountopacity.config.MountOpacityConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.passive.animal.HorseBaseEntity;
import net.minecraft.entity.living.mob.passive.animal.PigEntity;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.nio.FloatBuffer;

@Mixin(LivingEntityRenderer.class)
public abstract class MountOpacity1710Mixin {
    @Unique private float mountopacity$activeAlpha = 1;
    @Unique private final FloatBuffer mountopacity$currentColor = BufferUtils.createFloatBuffer(4);

    @WrapMethod(method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V")
    private void mountopacity$restoreState(LivingEntity entity, double x, double y, double z, float yaw,
                                         float tickDelta, Operation<Void> original) {
        LocalClientPlayerEntity player = Minecraft.getInstance().player;
        float alpha = player != null && entity == player.vehicle ? mountopacity$opacity(entity, player) : 1;
        if (alpha >= 1) { original.call(entity, x, y, z, yaw, tickDelta); return; }
        float previous = mountopacity$activeAlpha;
        mountopacity$activeAlpha = alpha;
        GL11.glPushAttrib(GL11.GL_CURRENT_BIT | GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        try { original.call(entity, x, y, z, yaw, tickDelta); }
        finally { GL11.glPopAttrib(); mountopacity$activeAlpha = previous; }
    }

    @Inject(method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/model/Model;prepare(Lnet/minecraft/entity/living/LivingEntity;FFF)V"))
    private void mountopacity$applyAlpha(LivingEntity entity, double x, double y, double z, float yaw,
                                        float tickDelta, CallbackInfo callback) {
        if (mountopacity$activeAlpha >= 1) return;
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1f);
        GLX.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        mountopacity$currentColor.clear();
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, mountopacity$currentColor);
        GL11.glColor4f(mountopacity$currentColor.get(0), mountopacity$currentColor.get(1),
                mountopacity$currentColor.get(2), mountopacity$activeAlpha);
    }

    @ModifyArg(method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL11;glColor4f(FFFF)V", remap = false), index = 3)
    private float mountopacity$coloredLayerAlpha(float alpha) { return alpha * mountopacity$activeAlpha; }

    @Unique private static float mountopacity$opacity(LivingEntity entity, LocalClientPlayerEntity player) {
        if (MountOpacityConfig.onlyInFirstPerson && Minecraft.getInstance().options.perspective != 0) return 1;
        float progress = Math.clamp((player.pitch + MountOpacityConfig.lookOffset) / MountOpacityConfig.lookRange, 0f, 1f);
        float configured = entity instanceof HorseBaseEntity ? MountOpacityConfig.horseOpacity
                : entity instanceof PigEntity ? MountOpacityConfig.pigOpacity : MountOpacityConfig.defaultOpacity;
        return (MountOpacityConfig.useLookDirection ? 100f - progress * (100f - configured) : configured) / 100f;
    }
}
//?}
