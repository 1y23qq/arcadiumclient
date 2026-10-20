package dev.microcontrollers.betterscreens;

import dev.microcontrollers.betterscreens.config.BetterScreensConfig;
import net.fabricmc.api.ModInitializer;

public class BetterScreens implements ModInitializer {
    @Override
    public void onInitialize() {
        BetterScreensConfig.INSTANCE.preload();
        ServerManager.initialize();
    }
}
