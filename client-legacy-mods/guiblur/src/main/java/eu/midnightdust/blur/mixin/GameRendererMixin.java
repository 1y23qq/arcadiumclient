package eu.midnightdust.blur.mixin;

import eu.midnightdust.blur.BlurRenderer;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "render(F)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/GameRenderer;lastWorldRenderTime:J", opcode = org.objectweb.asm.Opcodes.PUTFIELD, shift = At.Shift.AFTER))
	private void blur$render(float tickDelta, CallbackInfo ci) {
		BlurRenderer.render();
	}

	@Inject(method = "render(F)V", at = @At("TAIL"))
	private void blur$renderScreenId(float tickDelta, CallbackInfo ci) {
		BlurRenderer.renderScreenId();
	}
}
