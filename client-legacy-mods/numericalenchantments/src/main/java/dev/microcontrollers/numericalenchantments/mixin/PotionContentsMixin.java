package dev.microcontrollers.numericalenchantments.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.microcontrollers.numericalenchantments.config.NumericalEnchantmentsConfig;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.item.PotionItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PotionItem.class)
public class PotionContentsMixin {
    @WrapOperation(method = "addHoverText", at = @At(value = "INVOKE", target = "Lnet/minecraft/locale/I18n;translate(Ljava/lang/String;)Ljava/lang/String;", ordinal = 1))
    private static String replaceWithArabicNumeral(String key, Operation<String> original, @Local StatusEffectInstance statusEffectInstance) {
        return NumericalEnchantmentsConfig.enabled ? String.valueOf(statusEffectInstance.getAmplifier() + 1) : original.call(key);
    }
}
