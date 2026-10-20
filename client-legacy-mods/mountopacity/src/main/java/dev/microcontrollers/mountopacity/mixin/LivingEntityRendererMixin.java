//? if >1.7.10 {
/*package dev.microcontrollers.mountopacity.mixin;

import dev.microcontrollers.mountopacity.config.MountOpacityConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import org.polyfrost.oneconfig.internal.legacy.GlStateManager;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.passive.animal.HorseBaseEntity;
import net.minecraft.entity.living.mob.passive.animal.PigEntity;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin <T extends LivingEntity> {
    @Inject(method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/model/Model;prepare(Lnet/minecraft/entity/living/LivingEntity;FFF)V"))
    private void setOpacityPre(T livingEntity, double d, double e, double f, float g, float h, CallbackInfo ci) {
        if (livingEntity == Minecraft.getInstance().player.vehicle) {
            GlStateManager.enableAlphaTest();
            GlStateManager.enableBlend();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
            GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GlStateManager.color4f(1F, 1F, 1F, mountopacity$getOpacity(livingEntity, Minecraft.getInstance().player));
        }
    }

    @Inject(method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V", at = @At(value = "RETURN"))
    private void setOpacityPost(T livingEntity, double d, double e, double f, float g, float h, CallbackInfo ci) {
        if (livingEntity == Minecraft.getInstance().player.vehicle) {
            GlStateManager.color4f(1F, 1F, 1F, 1F);
            GlStateManager.disableBlend();
            GlStateManager.disableAlphaTest();
        }
    }

    @Unique
    private float mountopacity$getOpacity(T livingEntity, LocalClientPlayerEntity player) {
        if (MountOpacityConfig.onlyInFirstPerson && !(Minecraft.getInstance().options.perspective == 0)) return 100F;

        float opacity;
        float lookDownProgress = MathHelper.clamp((player.pitch + MountOpacityConfig.lookOffset) / MountOpacityConfig.lookRange, 0F, 1F);
        boolean dir =  MountOpacityConfig.useLookDirection;

        switch (livingEntity) {
            case HorseBaseEntity _ -> opacity = dir ? 100F - (lookDownProgress * (100F - MountOpacityConfig.horseOpacity)) : MountOpacityConfig.horseOpacity;
            case PigEntity _ -> opacity = dir ? 100F - (lookDownProgress * (100F - MountOpacityConfig.pigOpacity)) : MountOpacityConfig.pigOpacity;
            default -> opacity = dir ? 100F - (lookDownProgress * (100F - MountOpacityConfig.defaultOpacity)) : MountOpacityConfig.defaultOpacity;
        }

        return opacity / 100F;
    }
}
*///?}
