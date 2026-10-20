package dev.microcontrollers.overlaytweaks;

import dev.kikugie.fletching_table.annotation.fabric.Entrypoint;
import dev.microcontrollers.overlaytweaks.config.OverlayTweaksConfig;
import net.fabricmc.api.ModInitializer;

@Entrypoint
public class OverlayTweaks implements ModInitializer {
    @Override
    public void onInitialize() {
        OverlayTweaksConfig.INSTANCE.preload();
    }
}
