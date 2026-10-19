package io.github.arcadiumclient.featuretest.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import io.github.arcadiumclient.featuretest.Animations035Qa;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public abstract class AnimationRenderProbeMixin {
    @WrapMethod(method = "renderWorld")
    private void arcadium$worldScope(float tickDelta, long startTime, Operation<Void> original) {
        Animations035Qa.enterWorldDraw();
        try { original.call(tickDelta, startTime); }
        finally { Animations035Qa.exitWorldDraw(); }
    }

    @WrapMethod(method = "applyHurtCam")
    private void arcadium$hurtCameraState(float tickDelta, Operation<Void> original) {
        Animations035Qa.probeHurtCamera(tickDelta, original);
    }

    @WrapOperation(method = "renderItemInHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/ItemInHandRenderer;renderInFirstPerson(F)V"))
    private void arcadium$animationState(ItemInHandRenderer renderer, float tickDelta, Operation<Void> original) {
        Animations035Qa.beforeHandDraw();
        try { original.call(renderer, tickDelta); }
        finally { Animations035Qa.afterHandDraw(); }
    }
}
