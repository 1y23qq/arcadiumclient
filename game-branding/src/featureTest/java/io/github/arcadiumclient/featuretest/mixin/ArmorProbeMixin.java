package io.github.arcadiumclient.featuretest.mixin;
import io.github.arcadiumclient.featuretest.FeatureQa;

import net.minecraft.client.render.entity.layer.AbstractArmorLayer;
import net.minecraft.entity.living.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractArmorLayer.class, priority = 500)
public abstract class ArmorProbeMixin {
    @Inject(method = "renderArmor", at = @At("HEAD"))
    private void arcadium$armorDraw(LivingEntity entity, float a, float b, float c, float d, float e, float f, float g, int slot, CallbackInfo callback) {
        FeatureQa.armorCandidate(entity);
    }

    @Inject(method = "getModel", at = @At("HEAD"))
    private void arcadium$armorModel(int slot, CallbackInfoReturnable<?> callback) {
        FeatureQa.armorDraw(slot);
    }
}
