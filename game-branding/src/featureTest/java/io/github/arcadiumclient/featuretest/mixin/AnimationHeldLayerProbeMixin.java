package io.github.arcadiumclient.featuretest.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.featuretest.Animations035Qa;
import net.minecraft.client.render.entity.layer.ItemInHandLayer;
import net.minecraft.entity.living.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemInHandLayer.class)
public abstract class AnimationHeldLayerProbeMixin {
    @WrapMethod(method = "render")
    private void arcadium$heldEntityScope(LivingEntity entity, float limbAngle, float limbDistance, float tickDelta,
                                          float age, float headYaw, float headPitch, float scale, Operation<Void> original) {
        Animations035Qa.enterHeldLayer(entity);
        try { original.call(entity, limbAngle, limbDistance, tickDelta, age, headYaw, headPitch, scale); }
        finally { Animations035Qa.exitHeldLayer(); }
    }
}
