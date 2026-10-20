package dev.microcontrollers.confirmdisconnect;

import dev.microcontrollers.confirmdisconnect.config.ConfirmDisconnectConfig;
import net.fabricmc.api.ModInitializer;

public class ConfirmDisconnect implements ModInitializer {
    @Override
    public void onInitialize() {
        ConfirmDisconnectConfig.INSTANCE.preload();
    }
}
