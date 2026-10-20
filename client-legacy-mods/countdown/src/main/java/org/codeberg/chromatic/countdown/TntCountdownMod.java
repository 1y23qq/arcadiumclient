package org.codeberg.chromatic.countdown;

import net.fabricmc.api.ClientModInitializer;
import org.codeberg.chromatic.countdown.config.TntCountdownConfig;

public class TntCountdownMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        new TntCountdownConfig();
    }
}