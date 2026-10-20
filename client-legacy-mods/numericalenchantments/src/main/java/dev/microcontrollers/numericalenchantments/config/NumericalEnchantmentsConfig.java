package dev.microcontrollers.numericalenchantments.config;

import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;

public class NumericalEnchantmentsConfig extends Config {
    public static final NumericalEnchantmentsConfig INSTANCE = new NumericalEnchantmentsConfig();

    @Switch(
            title = "numerical-enchantments.use-arabic-numbers",
            description = "numerical-enchantments.use-arabic-numbers.description"
    )
    public static boolean enabled = true;

    public NumericalEnchantmentsConfig() {
        super("numericalenchantments.json", "/assets/numericalenchantments/icon.png", "Numerical Enchantments", Category.QOL);
        save();
    }
}
