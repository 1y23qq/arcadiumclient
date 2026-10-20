package dev.microcontrollers.numericalenchantments;

import dev.microcontrollers.numericalenchantments.config.NumericalEnchantmentsConfig;
import net.fabricmc.api.ModInitializer;

public class NumericalEnchantments implements ModInitializer {
	@Override
	public void onInitialize() {
		NumericalEnchantmentsConfig.INSTANCE.preload();
	}
}
