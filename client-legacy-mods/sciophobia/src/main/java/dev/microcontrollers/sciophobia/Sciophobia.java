package dev.microcontrollers.sciophobia;

import dev.microcontrollers.sciophobia.config.SciophobiaConfig;
import net.fabricmc.api.ModInitializer;

public class Sciophobia implements ModInitializer {
    @Override
    public void onInitialize() {
        SciophobiaConfig.INSTANCE.preload();
    }
}
