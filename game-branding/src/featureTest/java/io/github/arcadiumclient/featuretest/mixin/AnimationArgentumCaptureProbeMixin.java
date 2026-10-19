package io.github.arcadiumclient.featuretest.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.arcadiumclient.featuretest.Animations035Qa;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "dev.rdh.argentum.impl.render.entity.instancing.EntityCapture", remap = false)
public abstract class AnimationArgentumCaptureProbeMixin {
    @WrapOperation(method = "recordItem", at = @At(value = "INVOKE", target = "Ldev/rdh/argentum/impl/render/entity/instancing/EntityCapture;submit(Ldev/rdh/argentum/impl/render/entity/instancing/InstanceGeometry;Lorg/joml/Matrix4f;)Z", remap = false), remap = false)
    private boolean arcadium$submittedHeldMatrix(@Coerce Object capture, @Coerce Object geometry, @Coerce Object matrix,
                                                 Operation<Boolean> original, @Local(argsOnly = true) ItemStack stack) {
        boolean submitted = original.call(capture, geometry, matrix);
        if (submitted) Animations035Qa.recordCapturedHeldItem(stack, matrix);
        return submitted;
    }
}
