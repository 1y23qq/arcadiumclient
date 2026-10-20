package dev.microcontrollers.scrolltweaks.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.microcontrollers.scrolltweaks.config.ScrollTweaksConfig;
import net.minecraft.entity.living.player.PlayerInventory;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerInventory.class)
public class PlayerInventoryMixin {
    @Shadow public int selectedSlot;

    @WrapMethod(method = "scrollInHotbar")
    private void disableScroll(int amount, Operation<Void> original) {
        if (!ScrollTweaksConfig.disableScroll) original.call(amount);
    }

    @Inject(method = "scrollInHotbar", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/living/player/PlayerInventory;selectedSlot:I", ordinal = 0, opcode = Opcodes.GETFIELD))
    private void reverseScroll(int direction, CallbackInfo ci) {
        if (ScrollTweaksConfig.reverseScroll) {
            int dir;
            if (direction < 0) {
                dir = -2;
            } else {
                dir = 2;
            }
            this.selectedSlot += dir;
        }
    }

    @ModifyExpressionValue(method = "scrollInHotbar", at = {@At(value = "CONSTANT", args = "intValue=9", ordinal = 0), @At(value = "CONSTANT", args = "intValue=9", ordinal = 2)})
    private int preventOverflowScroll(int constant) {
        return ScrollTweaksConfig.preventOverflowScroll ? 1 : constant;
    }
}
