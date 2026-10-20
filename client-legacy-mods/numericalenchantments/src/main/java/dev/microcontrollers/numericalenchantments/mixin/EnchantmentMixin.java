package dev.microcontrollers.numericalenchantments.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.microcontrollers.numericalenchantments.config.NumericalEnchantmentsConfig;
import net.minecraft.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Enchantment.class)
public class EnchantmentMixin {
    @WrapOperation(method = "getName", at = @At(value = "INVOKE", target = "Lnet/minecraft/locale/I18n;translate(Ljava/lang/String;)Ljava/lang/String;", ordinal = 1))
    private static String replaceWithArabicNumeral(String key, Operation<String> original, int level) {
        return NumericalEnchantmentsConfig.enabled ? String.valueOf(level) : original.call(key);
    }
}
