package dev.microcontrollers.overlaytweaks.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.microcontrollers.overlaytweaks.config.OverlayTweaksConfig;
import net.minecraft.client.render.GameRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value = GameRenderer.class, priority = 1010)
public class GameRendererMixin {
   @ModifyExpressionValue(method = "renderItemInHand", at = @At(value = "FIELD", target = "Lnet/minecraft/client/options/GameOptions;hideGui:Z", opcode = Opcodes.GETFIELD))
    private boolean keepHand(boolean original) {
        return !OverlayTweaksConfig.keepHand && original;
    }
}
