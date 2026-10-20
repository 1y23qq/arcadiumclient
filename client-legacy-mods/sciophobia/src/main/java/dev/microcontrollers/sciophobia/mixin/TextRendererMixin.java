package dev.microcontrollers.sciophobia.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.microcontrollers.sciophobia.config.SciophobiaConfig;
import net.minecraft.client.render.TextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(TextRenderer.class)
public class TextRendererMixin {
    @ModifyVariable(method = "draw(Ljava/lang/String;IIIZ)I", at = @At("HEAD"), argsOnly = true, index = 5)
    private boolean modifyTextShadow(boolean shadow) {
        if (SciophobiaConfig.removeShadows) return false;
        else if (SciophobiaConfig.forceShadows) return true;
        else return shadow;
    }

    @WrapOperation(method = "draw(Ljava/lang/String;IIIZ)I", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/TextRenderer;drawLayer(Ljava/lang/String;IIIZ)I", ordinal = 0))
    private int alternateTextShadow(TextRenderer instance, String text, int x, int y, int color, boolean shadow, Operation<Integer> original) {
        if (SciophobiaConfig.fullShadow) {
            for (int j = -1; j <= 1; ++j) {
                for (int k = -1; k <= 1; ++k) {
                    if (j != 0 || k != 0) {
                        int xShadowOffset = -1 + j;
                        int yShadowOffset = -1 + k;
                        original.call(instance, text, x + xShadowOffset, y + yShadowOffset, color, shadow);
                    }
                }
            }
        } else if (SciophobiaConfig.alternateShadows) {
            return original.call(instance, text, x - 1, y, color, shadow);
        }
        return original.call(instance, text, x, y, color, shadow);
    }
}
