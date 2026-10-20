package dev.microcontrollers.overlaytweaks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.microcontrollers.overlaytweaks.config.OverlayTweaksConfig;
import net.minecraft.client.entity.particle.MobAppearanceParticle;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobAppearanceParticle.class)
public class ElderGuardianParticleRenderState {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private static void removeElderGuardianJumpscare(BufferBuilder bufferBuilder, Entity camera, float tickDelta, float dx, float dy, float dz, float forwards, float sideways, CallbackInfo ci) {
        if (OverlayTweaksConfig.elderGuardianOpacity == 0F) ci.cancel();
    }

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;color4f(FFFF)V"), index = 3)
    private static float changeElderGuardianOpacity(float g) {
        return g * OverlayTweaksConfig.elderGuardianOpacity / 100F / 0.55F;
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;scalef(FFF)V"))
    private static void changeElderGuardianScale(float x, float y, float z, Operation<Void> original) {
        original.call(OverlayTweaksConfig.elderGuardianScale, OverlayTweaksConfig.elderGuardianScale, OverlayTweaksConfig.elderGuardianScale);
    }
}
