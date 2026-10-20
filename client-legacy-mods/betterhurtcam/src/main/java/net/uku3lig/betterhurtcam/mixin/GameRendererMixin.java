package net.uku3lig.betterhurtcam.mixin;

import net.minecraft.client.render.GameRenderer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.uku3lig.betterhurtcam.BetterHurtCam;
import net.uku3lig.betterhurtcam.HurtCamType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "applyHurtCam", at = @At("HEAD"), cancellable = true)
	private void betterhurtcam$disableHurtCam(float tickDelta, CallbackInfo ci) {
		if (!BetterHurtCam.isEnabled()) {
			ci.cancel();
		}
	}

	@WrapOperation(method = "applyHurtCam", at = @At(
		value = "INVOKE",
		target = "Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V"
	))
	private void betterhurtcam$modifyHurtCam(float angle, float x, float y, float z, Operation<Void> original) {
		if (y != 0.0F && BetterHurtCam.getType() == HurtCamType.OLD) {
			angle = 0.0F;
		}
		original.call((float)(angle * BetterHurtCam.getMultiplier()), x, y, z);
	}
}
